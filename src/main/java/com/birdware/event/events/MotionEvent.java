package com.birdware.event.events;

import com.birdware.event.Event;

/**
 * Wraps {@code LocalPlayer#sendPosition}, which builds the per-tick movement packet.
 * <ul>
 *     <li>{@link Pre}: yaw, pitch and onGround may be changed; the values are applied to the player only while the
 *     packet is built and restored afterwards (silent rotations, NoFall, Criticals). Position is read-only.</li>
 *     <li>{@link Post}: the movement packet for this tick has been sent. The right moment for actions that must
 *     happen after the server knows the new rotation (attacks, placements).</li>
 * </ul>
 * Not posted while riding (vanilla sends vehicle packets instead). Instances are reused.
 */
public abstract class MotionEvent extends Event {
	public static final class Pre extends MotionEvent {
		public static final Pre INSTANCE = new Pre();

		private double x;
		private double y;
		private double z;
		private float yaw;
		private float pitch;
		private boolean onGround;
		private boolean rotationModified;

		private Pre() {
		}

		public Pre reset(double x, double y, double z, float yaw, float pitch, boolean onGround) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.yaw = yaw;
			this.pitch = pitch;
			this.onGround = onGround;
			this.rotationModified = false;
			return this;
		}

		public double getX() {
			return x;
		}

		public double getY() {
			return y;
		}

		public double getZ() {
			return z;
		}

		public float getYaw() {
			return yaw;
		}

		public float getPitch() {
			return pitch;
		}

		public boolean isOnGround() {
			return onGround;
		}

		public void setYaw(float yaw) {
			this.yaw = yaw;
			this.rotationModified = true;
		}

		public void setPitch(float pitch) {
			this.pitch = Math.max(-90f, Math.min(90f, pitch));
			this.rotationModified = true;
		}

		public void setRotation(float yaw, float pitch) {
			setYaw(yaw);
			setPitch(pitch);
		}

		public void setOnGround(boolean onGround) {
			this.onGround = onGround;
		}

		public boolean isRotationModified() {
			return rotationModified;
		}
	}

	public static final class Post extends MotionEvent {
		public static final Post INSTANCE = new Post();

		private Post() {
		}
	}
}
