package com.birdware.module.modules.combat;

import com.birdware.BirdWare;
import com.birdware.module.Category;
import com.birdware.module.Module;
import com.birdware.setting.BooleanSetting;
import com.birdware.setting.EnumSetting;
import com.birdware.setting.NumberSetting;
import com.birdware.setting.RangeSetting;
import com.birdware.setting.SettingGroup;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Chooses where on a target's hitbox KillAura and AimAssist aim. While this module is disabled they aim at the
 * closest point of the hitbox (minimal rotation, always inside reach).
 *
 * <p>Public API: {@link #aimPoint(LivingEntity, float)} (instance) and {@link #of(LivingEntity, float)} (static,
 * resolves the module and falls back to the closest point when it is missing or disabled).
 *
 * <p>The horizontal jitter is a time-based, eased random walk (not per-call noise), so per-tick callers (KillAura)
 * and per-frame callers (AimAssist) see the same smooth offset and the aim never "vibrates".
 */
public final class AimPoint extends Module {
	/** Hitbox regions. */
	public enum Mode implements EnumSetting.Nameable {
		HEAD("Head"),
		TORSO("Torso"),
		CENTER("Center"),
		LEGS("Legs"),
		CLOSEST("Closest"),
		DYNAMIC("Dynamic");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		@Override
		public String displayName() {
			return label;
		}
	}

	/** Fraction of the hitbox height used for the torso (chest) point. */
	private static final double TORSO_FRACTION = 0.68;
	/** Fraction of the hitbox height used for the legs point. */
	private static final double LEGS_FRACTION = 0.25;
	/** Keeps aim points this far inside the box so rounding never leaves the hitbox. */
	private static final double EDGE_MARGIN = 0.02;

	private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", "Head: eye level. Torso: chest. Center: middle of the hitbox. "
		+ "Legs: lower quarter. Closest: point of the hitbox nearest to your eyes. Dynamic: highest visible point among head, "
		+ "torso and legs (raycast), falling back to Closest when none is visible.", Mode.TORSO));
	private final NumberSetting verticalOffset = add(new NumberSetting("Vertical Offset",
		"Shifts the aim point up (positive) or down (negative); always clamped to the hitbox.", 0.0, -1.0, 1.0, 0.05).unit("m"));

	private final SettingGroup sgHuman = group("Humanize");
	private final NumberSetting jitter = sgHuman.add(new NumberSetting("Horizontal Jitter",
		"Random sideways offset as a fraction of the hitbox half-width, drifting smoothly over time for human-looking aim. 0 disables.",
		0.15, 0.0, 1.0, 0.05));
	private final RangeSetting jitterInterval = sgHuman.add(new RangeSetting("Jitter Interval",
		"How long each random offset takes to drift to the next one.", 180, 420, 50, 1500, 10).unit("ms"))
		.visibleWhen(() -> jitter.get() > 0);

	private final SettingGroup sgPrediction = group("Prediction");
	private final BooleanSetting prediction = sgPrediction.add(new BooleanSetting("Prediction",
		"Lead moving targets by their current velocity so the aim point is where they will be.", false));
	private final NumberSetting predictionTicks = sgPrediction.add(new NumberSetting("Prediction Ticks",
		"How many ticks of target velocity to lead by (roughly your ping / 50 ms).", 1.0, 0.0, 6.0, 0.25).unit(" ticks"))
		.visibleWhen(prediction::get);

	// Jitter random walk state (normalised offsets in [-1, 1]).
	private double jitterFromX;
	private double jitterFromZ;
	private double jitterToX;
	private double jitterToZ;
	private long jitterStart;
	private long jitterDurationNs = 1;

	// Dynamic mode cache: visibility is raycast at most once per tick per target.
	private int dynamicEntityId = Integer.MIN_VALUE;
	private int dynamicTick = Integer.MIN_VALUE;
	private Mode dynamicChoice = Mode.CLOSEST;

	public AimPoint() {
		super("AimPoint", "Selects the hitbox point KillAura and AimAssist aim at: head, torso, center, legs, closest point or the "
			+ "highest visible point, with optional vertical offset, smooth human-like jitter and velocity prediction.", Category.COMBAT);
	}

	@Override
	protected void onDisable() {
		dynamicEntityId = Integer.MIN_VALUE;
		dynamicTick = Integer.MIN_VALUE;
	}

	@Override
	public void onWorldLeave() {
		dynamicEntityId = Integer.MIN_VALUE;
		dynamicTick = Integer.MIN_VALUE;
	}

	@Override
	public String getInfo() {
		return EnumSetting.nameOf(mode.get());
	}

	public Mode getMode() {
		return mode.get();
	}

	/**
	 * Aim point for a target. Static convenience used by KillAura/AimAssist: uses this module when it is registered and
	 * enabled, otherwise the closest point of the (frame-interpolated) hitbox.
	 */
	public static Vec3 of(LivingEntity target, float partialTick) {
		BirdWare birdWare = BirdWare.get();
		AimPoint module = birdWare != null ? birdWare.modules().get(AimPoint.class) : null;
		if (module != null) return module.aimPoint(target, partialTick);
		return closestTo(lerpBox(target, partialTick), eyes(partialTick, target));
	}

	/**
	 * World-space point to aim at on {@code target}.
	 *
	 * @param partialTick frame interpolation (1.0 for tick logic = current positions)
	 * @return the configured point when the module is enabled, otherwise the closest hitbox point to the eyes
	 */
	public Vec3 aimPoint(LivingEntity target, float partialTick) {
		AABB box = lerpBox(target, partialTick);
		Vec3 eyes = eyes(partialTick, target);
		if (!isEnabled()) return closestTo(box, eyes);

		if (prediction.get() && predictionTicks.get() > 0) {
			double ticks = predictionTicks.get();
			box = box.move((target.getX() - target.xo) * ticks, (target.getY() - target.yo) * ticks, (target.getZ() - target.zo) * ticks);
		}

		Mode selected = mode.get();
		if (selected == Mode.DYNAMIC) selected = dynamicChoice(target, box, eyes);

		double x;
		double y;
		double z;
		if (selected == Mode.CLOSEST) {
			x = Mth.clamp(eyes.x, box.minX, box.maxX);
			y = Mth.clamp(eyes.y, box.minY, box.maxY);
			z = Mth.clamp(eyes.z, box.minZ, box.maxZ);
		} else {
			x = (box.minX + box.maxX) / 2.0;
			z = (box.minZ + box.maxZ) / 2.0;
			y = regionY(selected, target, box);
		}

		y += verticalOffset.get();
		double amount = jitter.get();
		if (amount > 0) {
			updateJitter();
			double t = jitterProgress();
			x += Mth.lerp(t, jitterFromX, jitterToX) * amount * box.getXsize() / 2.0;
			z += Mth.lerp(t, jitterFromZ, jitterToZ) * amount * box.getZsize() / 2.0;
		}
		return new Vec3(clampInside(x, box.minX, box.maxX), clampInside(y, box.minY, box.maxY), clampInside(z, box.minZ, box.maxZ));
	}

	// ------------------------------------------------------------------------------------------------ internals

	private static double regionY(Mode region, LivingEntity target, AABB box) {
		double height = box.getYsize();
		return switch (region) {
			// Eye height follows the pose (crouching, swimming), so the head point stays on the head.
			case HEAD -> Math.min(box.minY + target.getEyeHeight(), box.maxY - 0.1);
			case TORSO -> box.minY + height * TORSO_FRACTION;
			case LEGS -> box.minY + height * LEGS_FRACTION;
			default -> box.minY + height * 0.5;
		};
	}

	/** Highest visible region (head → torso → legs), cached per tick and target; Closest if all are hidden. */
	private Mode dynamicChoice(LivingEntity target, AABB box, Vec3 eyes) {
		LocalPlayer self = mc.player;
		int tick = self != null ? self.tickCount : 0;
		if (target.getId() == dynamicEntityId && tick == dynamicTick) return dynamicChoice;
		dynamicEntityId = target.getId();
		dynamicTick = tick;
		dynamicChoice = Mode.CLOSEST;
		double cx = (box.minX + box.maxX) / 2.0;
		double cz = (box.minZ + box.maxZ) / 2.0;
		for (Mode region : new Mode[]{Mode.HEAD, Mode.TORSO, Mode.LEGS}) {
			Vec3 point = new Vec3(cx, regionY(region, target, box), cz);
			if (!CombatHelper.isBlocked(eyes, point)) {
				dynamicChoice = region;
				break;
			}
		}
		return dynamicChoice;
	}

	private void updateJitter() {
		long now = System.nanoTime();
		if (now - jitterStart < jitterDurationNs) return;
		ThreadLocalRandom random = ThreadLocalRandom.current();
		jitterFromX = jitterToX;
		jitterFromZ = jitterToZ;
		jitterToX = random.nextDouble(-1.0, 1.0);
		jitterToZ = random.nextDouble(-1.0, 1.0);
		jitterStart = now;
		jitterDurationNs = Math.max(1L, (long) (jitterInterval.random() * 1_000_000L));
	}

	/** Smoothstep-eased progress of the current jitter segment. */
	private double jitterProgress() {
		double t = Mth.clamp((System.nanoTime() - jitterStart) / (double) jitterDurationNs, 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}

	private static double clampInside(double value, double min, double max) {
		if (max - min <= EDGE_MARGIN * 2) return (min + max) / 2.0;
		return Mth.clamp(value, min + EDGE_MARGIN, max - EDGE_MARGIN);
	}

	private static Vec3 closestTo(AABB box, Vec3 eyes) {
		return new Vec3(
			clampInside(eyes.x, box.minX, box.maxX),
			clampInside(eyes.y, box.minY, box.maxY),
			clampInside(eyes.z, box.minZ, box.maxZ));
	}

	/** Target hitbox interpolated to {@code partialTick} (1.0 → current tick position). */
	private static AABB lerpBox(LivingEntity target, float partialTick) {
		AABB box = target.getBoundingBox();
		if (partialTick >= 1.0f) return box;
		double dx = Mth.lerp(partialTick, target.xo, target.getX()) - target.getX();
		double dy = Mth.lerp(partialTick, target.yo, target.getY()) - target.getY();
		double dz = Mth.lerp(partialTick, target.zo, target.getZ()) - target.getZ();
		return box.move(dx, dy, dz);
	}

	private static Vec3 eyes(float partialTick, LivingEntity fallback) {
		LocalPlayer self = mc.player;
		if (self == null) return fallback.getBoundingBox().getCenter();
		return partialTick >= 1.0f ? self.getEyePosition() : self.getEyePosition(partialTick);
	}
}
