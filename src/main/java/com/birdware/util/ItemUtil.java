package com.birdware.util;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Item classification and scoring shared by AutoWeapon, AutoTool, AutoArmor, AutoEat, AutoPot, Scaffold,
 * InventoryManager, ChestStealer... All methods accept empty stacks.
 */
public final class ItemUtil {
	/** Base attack damage of a player's empty hand (attribute default). */
	public static final double BASE_ATTACK_DAMAGE = 1.0;
	/** Base attack speed of a player (attribute default). */
	public static final double BASE_ATTACK_SPEED = 4.0;

	private ItemUtil() {
	}

	// ------------------------------------------------------------------------------------------------ enchantments

	/** Level of an enchantment on the stack (0 if absent). No registry access needed. */
	public static int enchantmentLevel(ItemStack stack, ResourceKey<Enchantment> key) {
		if (stack.isEmpty()) return 0;
		ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		if (enchantments.isEmpty()) return 0;
		for (var entry : enchantments.entrySet()) {
			Holder<Enchantment> holder = entry.getKey();
			if (holder.is(key)) return entry.getIntValue();
		}
		return 0;
	}

	// ------------------------------------------------------------------------------------------------ weapons

	public static boolean isSword(ItemStack stack) {
		return stack.is(ItemTags.SWORDS);
	}

	public static boolean isAxe(ItemStack stack) {
		return stack.is(ItemTags.AXES);
	}

	public static boolean isSpear(ItemStack stack) {
		return stack.is(ItemTags.SPEARS);
	}

	public static boolean isMace(ItemStack stack) {
		return stack.is(Items.MACE);
	}

	/** Swords, axes, spears, tridents and maces. */
	public static boolean isMeleeWeapon(ItemStack stack) {
		return isSword(stack) || isAxe(stack) || isSpear(stack) || stack.is(Items.TRIDENT) || isMace(stack);
	}

	public static boolean isRangedWeapon(ItemStack stack) {
		return stack.is(Items.BOW) || stack.is(Items.CROSSBOW) || stack.is(Items.TRIDENT);
	}

	/** Main-hand attack damage from the item's attribute modifiers plus a Sharpness estimate. */
	public static double attackDamage(ItemStack stack) {
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		double damage = modifiers.compute(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE, EquipmentSlot.MAINHAND);
		int sharpness = enchantmentLevel(stack, Enchantments.SHARPNESS);
		if (sharpness > 0) damage += 0.5 * sharpness + 0.5;
		return damage;
	}

	/** Attacks per second when using this item. */
	public static double attackSpeed(ItemStack stack) {
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		return modifiers.compute(Attributes.ATTACK_SPEED, BASE_ATTACK_SPEED, EquipmentSlot.MAINHAND);
	}

	/** Damage per second assuming fully charged hits. */
	public static double dps(ItemStack stack) {
		return attackDamage(stack) * attackSpeed(stack);
	}

	// ------------------------------------------------------------------------------------------------ tools

	/** Mining speed against the block including Efficiency (vanilla formula: level^2 + 1 when the tool is effective). */
	public static float miningSpeed(ItemStack stack, BlockState state) {
		float speed = stack.getDestroySpeed(state);
		if (speed > 1f) {
			int efficiency = enchantmentLevel(stack, Enchantments.EFFICIENCY);
			if (efficiency > 0) speed += efficiency * efficiency + 1;
		}
		return speed;
	}

	public static boolean isTool(ItemStack stack) {
		return stack.has(DataComponents.TOOL);
	}

	// ------------------------------------------------------------------------------------------------ durability

	public static boolean isDamageable(ItemStack stack) {
		return stack.isDamageableItem();
	}

	/** Remaining durability in [0,1]; 1 for items without durability. */
	public static float durabilityFraction(ItemStack stack) {
		if (stack.isEmpty() || !stack.isDamageableItem()) return 1f;
		int max = stack.getMaxDamage();
		return max <= 0 ? 1f : (max - stack.getDamageValue()) / (float) max;
	}

	public static int remainingDurability(ItemStack stack) {
		return stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
	}

	// ------------------------------------------------------------------------------------------------ armor

	/** Equipment slot an item is worn in, or null when it is not wearable armor. */
	public static EquipmentSlot armorSlot(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable == null) return null;
		EquipmentSlot slot = equippable.slot();
		return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? slot : null;
	}

	public static boolean isArmor(ItemStack stack) {
		return armorSlot(stack) != null;
	}

	/**
	 * Protection score of an armor piece for its slot: armor points + toughness/2 + knockback resistance*10 plus
	 * enchantment weighting (Protection is the most valuable on anarchy servers due to crystals).
	 */
	public static double armorScore(ItemStack stack) {
		EquipmentSlot slot = armorSlot(stack);
		if (slot == null) return 0;
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		double armor = modifiers.compute(Attributes.ARMOR, 0, slot);
		double toughness = modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0, slot);
		double knockback = modifiers.compute(Attributes.KNOCKBACK_RESISTANCE, 0, slot);
		double score = armor + toughness * 0.5 + knockback * 10;
		score += enchantmentLevel(stack, Enchantments.PROTECTION) * 1.5;
		score += enchantmentLevel(stack, Enchantments.BLAST_PROTECTION) * 1.0;
		score += enchantmentLevel(stack, Enchantments.PROJECTILE_PROTECTION) * 0.5;
		score += enchantmentLevel(stack, Enchantments.FIRE_PROTECTION) * 0.5;
		score += enchantmentLevel(stack, Enchantments.UNBREAKING) * 0.1;
		score += enchantmentLevel(stack, Enchantments.MENDING) * 0.2;
		return score;
	}

	// ------------------------------------------------------------------------------------------------ consumables

	public static FoodProperties food(ItemStack stack) {
		return stack.get(DataComponents.FOOD);
	}

	public static boolean isFood(ItemStack stack) {
		return stack.has(DataComponents.FOOD);
	}

	/** Food that is harmful or wasteful to auto-eat (poison, hunger, chorus teleport, suspicious stew). */
	public static boolean isUnsafeFood(ItemStack stack) {
		return stack.is(Items.ROTTEN_FLESH) || stack.is(Items.SPIDER_EYE) || stack.is(Items.POISONOUS_POTATO)
			|| stack.is(Items.PUFFERFISH) || stack.is(Items.CHORUS_FRUIT) || stack.is(Items.SUSPICIOUS_STEW)
			|| stack.is(Items.CHICKEN);
	}

	public static boolean isGoldenApple(ItemStack stack) {
		return stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
	}

	public static boolean isTotem(ItemStack stack) {
		return stack.has(DataComponents.DEATH_PROTECTION);
	}

	/** True if any potion effect on the stack matches the given effect. */
	public static boolean hasEffect(ItemStack stack, Holder<MobEffect> effect) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null) return false;
		for (MobEffectInstance instance : contents.getAllEffects()) {
			if (instance.getEffect().equals(effect)) return true;
		}
		return false;
	}

	public static boolean isThrowablePotion(ItemStack stack) {
		return stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
	}

	public static boolean isDrinkablePotion(ItemStack stack) {
		return stack.is(Items.POTION);
	}

	// ------------------------------------------------------------------------------------------------ blocks

	/** A block item that places a full, solid, non-falling block (Scaffold/Tower material). */
	public static boolean isPlaceableSolidBlock(ItemStack stack) {
		if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
		Block block = blockItem.getBlock();
		if (block instanceof FallingBlock) return false;
		BlockState state = block.defaultBlockState();
		return state.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)
			&& !state.hasBlockEntity();
	}

	public static Block blockOf(ItemStack stack) {
		return stack.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
	}
}
