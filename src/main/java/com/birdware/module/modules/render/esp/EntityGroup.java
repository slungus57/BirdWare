package com.birdware.module.modules.render.esp;

import com.birdware.setting.EnumSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.VehicleEntity;

/**
 * Coarse entity classification shared by ESP, Tracers, Chams and Nametags so every render module agrees on what a
 * "hostile" or "passive" entity is. Classification is allocation free (a handful of instanceof checks plus the cached
 * friend lookup).
 */
public enum EntityGroup implements EnumSetting.Nameable {
	PLAYER("Players"),
	FRIEND("Friends"),
	HOSTILE("Hostiles"),
	PASSIVE("Passives"),
	ITEM("Items"),
	PROJECTILE("Projectiles"),
	OTHER("Other");

	/** Cached {@code values()} (the JDK clones the array on every call). */
	public static final EntityGroup[] VALUES = values();

	private final String displayName;

	EntityGroup(String displayName) {
		this.displayName = displayName;
	}

	@Override
	public String displayName() {
		return displayName;
	}

	@Override
	public String toString() {
		return displayName;
	}

	/**
	 * Classifies an entity, or returns null for entities no render module cares about (markers, display entities,
	 * area effect clouds, lightning...). Players on the friend list are {@link #FRIEND}. Every {@link Enemy} is
	 * hostile; every other AI mob (animals, villagers, golems, fish, bats, allays...) is passive.
	 */
	public static EntityGroup of(Entity entity) {
		if (entity instanceof Player) return FriendCache.isFriend(entity) ? FRIEND : PLAYER;
		if (entity instanceof Enemy) return HOSTILE;
		if (entity instanceof Mob) return PASSIVE;
		if (entity instanceof ItemEntity) return ITEM;
		if (entity instanceof Projectile) return PROJECTILE;
		return OtherKind.of(entity) != null ? OTHER : null;
	}

	/** Sub-types of {@link #OTHER}, individually selectable by modules. */
	public enum OtherKind {
		CRYSTALS("Crystals"),
		VEHICLES("Vehicles"),
		ARMOR_STANDS("Armor Stands"),
		TNT("TNT"),
		XP_ORBS("XP Orbs"),
		FALLING_BLOCKS("Falling Blocks");

		public static final OtherKind[] VALUES = values();

		private final String label;

		OtherKind(String label) {
			this.label = label;
		}

		/** Option label used in multi-select settings. */
		public String label() {
			return label;
		}

		public static OtherKind of(Entity entity) {
			if (entity instanceof EndCrystal) return CRYSTALS;
			if (entity instanceof VehicleEntity) return VEHICLES;
			if (entity instanceof ArmorStand) return ARMOR_STANDS;
			if (entity instanceof PrimedTnt) return TNT;
			if (entity instanceof ExperienceOrb) return XP_ORBS;
			if (entity instanceof FallingBlockEntity) return FALLING_BLOCKS;
			return null;
		}

		/** All option labels in declaration order. */
		public static java.util.List<String> labels() {
			java.util.List<String> list = new java.util.ArrayList<>(VALUES.length);
			for (OtherKind kind : VALUES) list.add(kind.label);
			return list;
		}
	}
}
