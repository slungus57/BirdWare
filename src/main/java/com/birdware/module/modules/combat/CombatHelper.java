package com.birdware.module.modules.combat;

import com.birdware.BirdWare;
import com.birdware.module.Module;
import com.birdware.setting.EnumSetting;
import com.birdware.util.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Small, allocation-light helpers shared by the combat modules (KillAura, TriggerBot, AimAssist, AutoClicker):
 * critical-hit conditions mirroring {@code Player#canCriticalAttack}, ray/box intersection, wall checks, weapon
 * filters and randomised click intervals.
 */
final class CombatHelper {
	private static final Minecraft mc = Minecraft.getInstance();

	private CombatHelper() {
	}

	/** Weapon requirement used by KillAura and TriggerBot. */
	enum WeaponFilter implements EnumSetting.Nameable {
		NONE("None"),
		SWORD("Sword"),
		AXE("Axe"),
		SWORD_AXE("Sword / Axe"),
		MELEE("Any Melee Weapon");

		private final String label;

		WeaponFilter(String label) {
			this.label = label;
		}

		@Override
		public String displayName() {
			return label;
		}

		boolean accepts(ItemStack stack) {
			return switch (this) {
				case NONE -> true;
				case SWORD -> ItemUtil.isSword(stack);
				case AXE -> ItemUtil.isAxe(stack);
				case SWORD_AXE -> ItemUtil.isSword(stack) || ItemUtil.isAxe(stack);
				case MELEE -> ItemUtil.isMeleeWeapon(stack);
			};
		}
	}

	/** Random distribution for click intervals. */
	enum Distribution implements EnumSetting.Nameable {
		UNIFORM("Uniform"),
		GAUSSIAN("Gaussian");

		private final String label;

		Distribution(String label) {
			this.label = label;
		}

		@Override
		public String displayName() {
			return label;
		}
	}

	/** Whether a module with the given display name is registered and enabled (cooperation without hard deps). */
	static boolean isModuleEnabled(String name) {
		BirdWare birdWare = BirdWare.get();
		if (birdWare == null) return false;
		Module module = birdWare.modules().get(name);
		return module != null && module.isEnabled();
	}

	/**
	 * State-independent crit requirements of {@code Player#canCriticalAttack} (everything except falling and
	 * sprinting): no water, ladders, vehicles, blindness or creative flight.
	 */
	static boolean critPossible(LocalPlayer player) {
		return !player.onClimbable()
			&& !player.isInWater()
			&& !player.isMobilityRestricted()
			&& !player.isPassenger()
			&& !player.getAbilities().flying;
	}

	/** True while a hit would be a vanilla critical (ignoring sprint, which callers handle separately). */
	static boolean inCritWindow(LocalPlayer player) {
		return player.fallDistance > 0.0 && !player.onGround() && critPossible(player);
	}

	/** Movement input is active (WASD). */
	static boolean hasMovementInput(LocalPlayer player) {
		return player.input != null && player.input.getMoveVector().lengthSquared() > 1.0E-4f;
	}

	/**
	 * Point where the segment {@code from → to} enters {@code box}, {@code from} itself when it starts inside the box,
	 * or null when the segment misses.
	 */
	static Vec3 rayEntry(AABB box, Vec3 from, Vec3 to) {
		if (box.contains(from)) return from;
		Optional<Vec3> hit = box.clip(from, to);
		return hit.orElse(null);
	}

	/** True when a block collision shape lies between the two points. */
	static boolean isBlocked(Vec3 from, Vec3 to) {
		if (mc.level == null || mc.player == null) return false;
		HitResult result = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
		return result.getType() != HitResult.Type.MISS;
	}

	/** Squared distance from {@code from} to the first block hit along the segment, or {@code Double.MAX_VALUE}. */
	static double blockHitDistanceSq(Vec3 from, Vec3 to, Entity viewer) {
		if (mc.level == null) return Double.MAX_VALUE;
		HitResult result = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, viewer));
		return result.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : from.distanceToSqr(result.getLocation());
	}

	/**
	 * Milliseconds until the next click for a CPS range. Gaussian draws cluster around the middle of the range (more
	 * human), uniform draws spread evenly; both stay inside [low, high].
	 */
	static double clickIntervalMs(double lowCps, double highCps, Distribution distribution) {
		double low = Math.max(0.1, Math.min(lowCps, highCps));
		double high = Math.max(low, Math.max(lowCps, highCps));
		ThreadLocalRandom random = ThreadLocalRandom.current();
		double cps;
		if (high - low < 1.0E-6) {
			cps = low;
		} else if (distribution == Distribution.GAUSSIAN) {
			double mean = (low + high) / 2.0;
			double deviation = (high - low) / 4.0;
			cps = Math.max(low, Math.min(high, mean + random.nextGaussian() * deviation));
		} else {
			cps = random.nextDouble(low, high);
		}
		return 1000.0 / cps;
	}
}
