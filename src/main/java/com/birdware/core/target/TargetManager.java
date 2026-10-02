package com.birdware.core.target;

import com.birdware.BirdWare;
import com.birdware.event.EventPriority;
import com.birdware.event.Subscribe;
import com.birdware.event.events.TickEvent;
import com.birdware.event.events.WorldChangeEvent;
import com.birdware.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shared target acquisition. Each tick the living entities near the player are collected once (bounded radius) and
 * every module queries that cache with its own {@link TargetFilter} and {@link TargetSort}, so KillAura, AimAssist,
 * TriggerBot and visual modules do not each walk the whole entity list.
 *
 * <p>Also tracks the "current combat target" announced by combat modules ({@link #setCombatTarget}) for the
 * TargetHUD and Criticals/AutoWeapon cooperation.
 */
public final class TargetManager {
	/** Entities beyond this radius are never collected (covers every module's max range). */
	public static final double SCAN_RADIUS = 64.0;

	private final Minecraft mc = Minecraft.getInstance();
	private final List<LivingEntity> nearby = new ArrayList<>();
	private final List<LivingEntity> scratch = new ArrayList<>();
	private LivingEntity combatTarget;
	private Object combatTargetOwner;
	private long combatTargetTime;

	public void init() {
		BirdWare.get().events().subscribe(this);
	}

	@Subscribe(priority = EventPriority.HIGHEST)
	private void onTick(TickEvent.Pre event) {
		nearby.clear();
		double maxSq = SCAN_RADIUS * SCAN_RADIUS;
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity == mc.player || !(entity instanceof LivingEntity living) || !living.isAlive()) continue;
			if (entity.distanceToSqr(mc.player) > maxSq) continue;
			nearby.add(living);
		}
		if (combatTarget != null && (combatTarget.isRemoved() || !combatTarget.isAlive()
			|| System.currentTimeMillis() - combatTargetTime > 1500)) {
			combatTarget = null;
			combatTargetOwner = null;
		}
	}

	@Subscribe
	private void onWorldChange(WorldChangeEvent event) {
		nearby.clear();
		combatTarget = null;
		combatTargetOwner = null;
	}

	/** Living entities (excluding the player) within {@link #SCAN_RADIUS}, refreshed each tick. Do not modify. */
	public List<LivingEntity> getNearby() {
		return nearby;
	}

	/**
	 * All valid targets sorted best-first into {@code out} (cleared first). Returns {@code out}.
	 */
	public List<LivingEntity> findAll(TargetFilter filter, TargetSort sort, List<LivingEntity> out) {
		out.clear();
		if (mc.player == null) return out;
		for (LivingEntity entity : nearby) {
			if (filter.test(entity)) out.add(entity);
		}
		if (out.size() > 1) out.sort(comparator(sort));
		return out;
	}

	/** The single best target or null. */
	public LivingEntity findBest(TargetFilter filter, TargetSort sort) {
		if (mc.player == null) return null;
		LivingEntity best = null;
		Comparator<LivingEntity> comparator = comparator(sort);
		for (LivingEntity entity : nearby) {
			if (!filter.test(entity)) continue;
			if (best == null || comparator.compare(entity, best) < 0) best = entity;
		}
		return best;
	}

	/** Number of valid targets (e.g. for ArrayList info). */
	public int count(TargetFilter filter) {
		int n = 0;
		for (LivingEntity entity : nearby) {
			if (filter.test(entity)) n++;
		}
		return n;
	}

	public Comparator<LivingEntity> comparator(TargetSort sort) {
		return switch (sort) {
			case DISTANCE -> Comparator.comparingDouble(RotationUtil::eyeDistanceSqToBox);
			case HEALTH -> Comparator.comparingDouble(TargetManager::effectiveHealth);
			case HIGHEST_HEALTH -> Comparator.comparingDouble((LivingEntity e) -> effectiveHealth(e)).reversed();
			case ARMOR -> Comparator.comparingInt(LivingEntity::getArmorValue);
			case ANGLE -> Comparator.comparingDouble(e -> RotationUtil.angleTo(RotationUtil.closestPoint(e.getBoundingBox())));
			case HURT_TIME -> Comparator.comparingInt((LivingEntity e) -> e.hurtTime);
			case THREAT -> Comparator.comparingDouble(TargetManager::threat).reversed();
		};
	}

	/** Health plus absorption. */
	public static float effectiveHealth(LivingEntity entity) {
		return entity.getHealth() + entity.getAbsorptionAmount();
	}

	/** Heuristic danger score: close, armored, healthy players first. */
	public static double threat(LivingEntity entity) {
		double distance = Math.sqrt(RotationUtil.eyeDistanceSqToBox(entity));
		double score = 10.0 / Math.max(0.5, distance);
		if (entity instanceof Player) score *= 3;
		score += entity.getArmorValue() * 0.2;
		score += effectiveHealth(entity) * 0.05;
		return score;
	}

	// ------------------------------------------------------------------------------------------------ combat target

	/** Announces the entity a combat module is currently fighting (shown in TargetHUD). Call every tick. */
	public void setCombatTarget(Object owner, LivingEntity target) {
		if (target == null) {
			clearCombatTarget(owner);
			return;
		}
		combatTarget = target;
		combatTargetOwner = owner;
		combatTargetTime = System.currentTimeMillis();
	}

	public void clearCombatTarget(Object owner) {
		if (combatTargetOwner == owner) {
			combatTarget = null;
			combatTargetOwner = null;
		}
	}

	/** Current combat target or null (expires 1.5 s after the last announcement). */
	public LivingEntity getCombatTarget() {
		return combatTarget;
	}

	/** Scratch list for callers that need a temporary list on the client thread without allocating. */
	public List<LivingEntity> scratchList() {
		scratch.clear();
		return scratch;
	}
}
