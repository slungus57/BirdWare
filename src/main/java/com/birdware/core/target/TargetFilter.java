package com.birdware.core.target;

import com.birdware.BirdWare;
import com.birdware.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;

/**
 * Mutable, reusable description of which entities are valid targets. Modules keep one instance and refresh it from
 * their settings (see {@link TargetSettings#apply(TargetFilter)}) before querying {@link TargetManager}.
 */
public final class TargetFilter {
	private static final Minecraft mc = Minecraft.getInstance();

	public boolean players = true;
	public boolean hostiles = true;
	public boolean neutrals = false;
	public boolean passives = false;
	public boolean villagers = false;
	public boolean golems = false;
	public boolean invisibles = true;
	public boolean friends = false;
	public boolean armorStands = false;
	public boolean ignoreNaked = false;
	public boolean ignoreCreative = true;
	/** Skip players missing from the tab list (server-side NPCs / anti-cheat bots). */
	public boolean antiBot = true;
	public boolean ignoreTeammates = false;
	/** Require a clear line of sight; otherwise {@link #wallRange} applies to obstructed targets. */
	public boolean requireVisible = false;
	/** Max distance (eyes to closest hitbox point). */
	public double range = 4.0;
	/** Max distance for targets behind walls (only when !requireVisible). */
	public double wallRange = 3.0;
	/** Field of view in degrees around the camera direction; 360 = everything. */
	public float fov = 360f;

	/** Category check only (no distance/visibility). */
	public boolean matchesType(Entity entity) {
		if (!(entity instanceof LivingEntity living)) return false;
		if (entity instanceof ArmorStand) return armorStands;
		if (entity instanceof Player player) {
			if (!players) return false;
			if (!friends && BirdWare.get().friends().isFriend(player)) return false;
			if (ignoreCreative && (player.isCreative() || player.isSpectator())) return false;
			if (ignoreNaked && isNaked(player)) return false;
			if (antiBot && isBot(player)) return false;
			if (ignoreTeammates && mc.player != null && mc.player.isAlliedTo(player)) return false;
			return true;
		}
		if (entity instanceof Enemy) return hostiles;
		if (entity instanceof NeutralMob) return neutrals;
		if (entity instanceof Npc) return villagers;
		if (entity instanceof AbstractGolem) return golems;
		if (entity instanceof Animal || entity instanceof WaterAnimal || entity instanceof AmbientCreature) return passives;
		return living.getType().getCategory().isFriendly() ? passives : hostiles;
	}

	/** Full validity check: alive, type, invisibility, range, visibility and FOV. */
	public boolean test(Entity entity) {
		LocalPlayer self = mc.player;
		if (self == null || entity == self || entity == mc.getCameraEntity()) return false;
		if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isDeadOrDying() || entity.isRemoved()) return false;
		if (!invisibles && entity.isInvisible()) return false;
		if (!matchesType(entity)) return false;
		double distSq = RotationUtil.eyeDistanceSqToBox(entity);
		if (distSq > range * range) return false;
		boolean visible = self.hasLineOfSight(entity);
		if (!visible) {
			if (requireVisible) return false;
			if (distSq > wallRange * wallRange) return false;
		}
		if (fov < 360f) {
			float angle = RotationUtil.angleTo(RotationUtil.closestPoint(entity.getBoundingBox()));
			if (angle > fov / 2f) return false;
		}
		return true;
	}

	private static boolean isNaked(Player player) {
		for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
			if (slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR && !player.getItemBySlot(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** A player entity that the server did not list in the tab list (typical NPC/bot). */
	public static boolean isBot(Player player) {
		if (mc.getConnection() == null) return false;
		return mc.getConnection().getPlayerInfo(player.getUUID()) == null;
	}
}
