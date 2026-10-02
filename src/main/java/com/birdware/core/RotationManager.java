package com.birdware.core;

import com.birdware.BirdWare;
import com.birdware.event.EventPriority;
import com.birdware.event.Subscribe;
import com.birdware.event.events.MotionEvent;
import com.birdware.event.events.TickEvent;
import com.birdware.event.events.WorldChangeEvent;
import com.birdware.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Shared rotation arbitration. Modules never write the player's rotation directly; they {@link #request} one each
 * tick and the highest-priority request wins. The winner is eased toward its target with a per-tick speed limit
 * and mouse-sensitivity GCD quantisation, then applied either silently (only in the movement packet, camera
 * untouched) or visibly (player's view turns).
 *
 * <p>Requests expire after {@code ticks} ticks (default 1), so a module that stops requesting releases the rotation
 * automatically; {@link #release(Object)} releases immediately. After the last request ends, the server rotation
 * eases back to the camera rotation ({@link #setReturnSpeed}) to avoid an instant snap ("restoration").
 *
 * <p>Typical use (KillAura):
 * <pre>
 * {@literal @}Subscribe void onTick(TickEvent.Pre e) { rotations.request(this, yaw, pitch, Priority.COMBAT, speed, true); }
 * {@literal @}Subscribe void onPost(MotionEvent.Post e) { if (rotations.isFacing(yaw, pitch, 15f)) attack(); }
 * </pre>
 */
public final class RotationManager {
	/** Conventional priorities; any int works. */
	public static final class Priority {
		public static final int LOW = 0;
		public static final int AUTOMATION = 25;
		public static final int WORLD = 50;
		public static final int COMBAT = 75;
		public static final int CRITICAL = 100;

		private Priority() {
		}
	}

	private static final class Request {
		float yaw;
		float pitch;
		int priority;
		float speed;
		boolean silent;
		int ticksLeft;
		long sequence;
	}

	private final Minecraft mc = Minecraft.getInstance();
	private final Map<Object, Request> requests = new IdentityHashMap<>();
	private long sequence;

	private Object owner;
	private boolean active;
	private boolean returning;
	private float serverYaw;
	private float serverPitch;
	private float returnSpeed = 45f;
	private boolean gcdFix = true;

	public void init() {
		BirdWare.get().events().subscribe(this);
	}

	/**
	 * Requests a rotation for one tick.
	 * @param speed  maximum change in degrees per tick (use 180 for instant)
	 * @param silent true: only the server sees it; false: the player's camera turns too
	 */
	public void request(Object requester, float yaw, float pitch, int priority, float speed, boolean silent) {
		request(requester, yaw, pitch, priority, speed, silent, 1);
	}

	/** Requests a rotation that stays active for {@code ticks} ticks unless renewed or released. */
	public void request(Object requester, float yaw, float pitch, int priority, float speed, boolean silent, int ticks) {
		Request r = requests.computeIfAbsent(requester, k -> new Request());
		r.yaw = yaw;
		r.pitch = Mth.clamp(pitch, -90f, 90f);
		r.priority = priority;
		r.speed = Math.max(1f, speed);
		r.silent = silent;
		r.ticksLeft = Math.max(1, ticks);
		r.sequence = ++sequence;
	}

	/** Drops the requester's rotation request immediately. */
	public void release(Object requester) {
		requests.remove(requester);
		if (owner == requester) owner = null;
	}

	/** True when the requester currently controls the rotation. */
	public boolean isOwner(Object requester) {
		return active && owner == requester;
	}

	public Object getOwner() {
		return active ? owner : null;
	}

	/** True while BirdWare is overriding the rotation sent to the server (including the return phase). */
	public boolean isActive() {
		return active || returning;
	}

	/** Rotation last sent to the server (camera rotation when inactive). */
	public float getServerYaw() {
		return isActive() ? serverYaw : (mc.player != null ? mc.player.getYRot() : 0f);
	}

	public float getServerPitch() {
		return isActive() ? serverPitch : (mc.player != null ? mc.player.getXRot() : 0f);
	}

	/** Whether the last sent rotation is within {@code tolerance} degrees (each axis) of the given rotation. */
	public boolean isFacing(float yaw, float pitch, float tolerance) {
		return Math.abs(Mth.wrapDegrees(yaw - getServerYaw())) <= tolerance
			&& Math.abs(pitch - getServerPitch()) <= tolerance;
	}

	public void setReturnSpeed(float degreesPerTick) {
		this.returnSpeed = Math.max(1f, degreesPerTick);
	}

	public void setGcdFix(boolean enabled) {
		this.gcdFix = enabled;
	}

	@Subscribe(priority = EventPriority.HIGHEST)
	private void onMotion(MotionEvent.Pre event) {
		if (mc.player == null) return;
		Request best = null;
		Object bestOwner = null;
		for (Map.Entry<Object, Request> entry : requests.entrySet()) {
			Request r = entry.getValue();
			if (r.ticksLeft <= 0) continue;
			if (best == null || r.priority > best.priority || (r.priority == best.priority && r.sequence > best.sequence)) {
				best = r;
				bestOwner = entry.getKey();
			}
		}

		float cameraYaw = mc.player.getYRot();
		float cameraPitch = mc.player.getXRot();
		if (!active && !returning) {
			serverYaw = cameraYaw;
			serverPitch = cameraPitch;
		}

		if (best != null) {
			owner = bestOwner;
			active = true;
			returning = false;
			step(best.yaw, best.pitch, best.speed);
			if (!best.silent) {
				mc.player.setYRot(serverYaw);
				mc.player.setXRot(serverPitch);
				mc.player.setYHeadRot(serverYaw);
			}
			event.setRotation(serverYaw, serverPitch);
			return;
		}

		owner = null;
		if (active) {
			active = false;
			returning = true;
		}
		if (returning) {
			step(cameraYaw, cameraPitch, returnSpeed);
			if (Math.abs(Mth.wrapDegrees(serverYaw - cameraYaw)) < 1f && Math.abs(serverPitch - cameraPitch) < 1f) {
				returning = false;
				return;
			}
			event.setRotation(serverYaw, serverPitch);
		}
	}

	private void step(float targetYaw, float targetPitch, float speed) {
		float yawDelta = Mth.wrapDegrees(targetYaw - serverYaw);
		float pitchDelta = targetPitch - serverPitch;
		float length = (float) Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
		if (length > speed) {
			// Scale both axes together so the path is straight, like a human flick.
			float scale = speed / length;
			yawDelta *= scale;
			pitchDelta *= scale;
		}
		if (gcdFix) {
			float gcd = RotationUtil.getGcd();
			yawDelta = Math.round(yawDelta / gcd) * gcd;
			pitchDelta = Math.round(pitchDelta / gcd) * gcd;
		}
		serverYaw += yawDelta;
		serverPitch = Mth.clamp(serverPitch + pitchDelta, -90f, 90f);
	}

	@Subscribe(priority = EventPriority.LOWEST)
	private void onTickEnd(TickEvent.Post event) {
		requests.values().removeIf(r -> --r.ticksLeft <= 0);
	}

	@Subscribe
	private void onWorldChange(WorldChangeEvent event) {
		requests.clear();
		owner = null;
		active = false;
		returning = false;
	}
}
