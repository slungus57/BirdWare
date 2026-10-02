package com.birdware.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Block placement and breaking helpers (Scaffold, Tower, Nuker, AutoMine, Surround-style modules).
 */
public final class BlockUtil {
	private static final Minecraft mc = Minecraft.getInstance();

	/** Default block interaction range when the attribute is unavailable. */
	public static final double DEFAULT_REACH = 4.5;

	private BlockUtil() {
	}

	public static BlockState state(BlockPos pos) {
		return mc.level.getBlockState(pos);
	}

	/** Air, fluids, grass... anything a placed block replaces. */
	public static boolean isReplaceable(BlockPos pos) {
		return state(pos).canBeReplaced();
	}

	/** Block interaction reach of the player (attribute based, honours creative). */
	public static double reach() {
		return mc.player != null ? mc.player.blockInteractionRange() : DEFAULT_REACH;
	}

	/** True if a block placed at pos would not intersect an entity. */
	public static boolean isUnobstructed(BlockPos pos, Block block) {
		return mc.level.isUnobstructed(block.defaultBlockState(), pos, CollisionContext.empty());
	}

	/** Solid neighbour faces a block at pos could be placed against. */
	public record PlaceTarget(BlockPos neighbor, Direction face, Vec3 hitVec) {
		/** Hit result clicking the neighbour's face. */
		public BlockHitResult hitResult() {
			return new BlockHitResult(hitVec, face, neighbor, false);
		}
	}

	/**
	 * Finds the best neighbour to click for placing at {@code pos}: a non-replaceable neighbour whose face points to
	 * pos, preferring faces closest to the player's eyes and within reach. Returns null if none.
	 * @param strictVisibility require the clicked face to face the player (stricter anti-cheats)
	 */
	public static PlaceTarget findPlaceTarget(BlockPos pos, boolean strictVisibility) {
		if (mc.player == null || mc.level == null) return null;
		Vec3 eyes = mc.player.getEyePosition();
		double reach = reach();
		PlaceTarget best = null;
		double bestDist = Double.MAX_VALUE;
		for (Direction direction : Direction.values()) {
			BlockPos neighbor = pos.relative(direction);
			BlockState neighborState = state(neighbor);
			if (neighborState.canBeReplaced()) continue;
			Direction face = direction.getOpposite();
			Vec3 hitVec = Vec3.atCenterOf(neighbor).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5));
			if (strictVisibility) {
				Vec3 toEyes = eyes.subtract(hitVec);
				if (toEyes.dot(Vec3.atLowerCornerOf(face.getUnitVec3i())) <= 0) continue;
			}
			double dist = eyes.distanceToSqr(hitVec);
			if (dist > reach * reach) continue;
			if (dist < bestDist) {
				bestDist = dist;
				best = new PlaceTarget(neighbor, face, hitVec);
			}
		}
		return best;
	}

	/**
	 * Places the held block at the target via {@code MultiPlayerGameMode#useItemOn}.
	 * @param swing  client-side hand swing animation (a swing packet is always sent when false)
	 * @return true when the server was asked to place
	 */
	public static boolean place(PlaceTarget target, InteractionHand hand, boolean swing) {
		if (mc.player == null || mc.gameMode == null || target == null) return false;
		InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, target.hitResult());
		if (!result.consumesAction()) return false;
		swing(hand, swing);
		return true;
	}

	/** Swings the hand visibly or only sends the swing packet. */
	public static void swing(InteractionHand hand, boolean visible) {
		if (mc.player == null) return;
		if (visible) mc.player.swing(hand);
		else if (mc.getConnection() != null) mc.getConnection().send(new ServerboundSwingPacket(hand));
	}

	/** True if the block can be mined (not air/fluid, not unbreakable, in reach). */
	public static boolean canBreak(BlockPos pos) {
		if (mc.player == null || mc.level == null) return false;
		BlockState state = state(pos);
		if (state.isAir() || state.liquid()) return false;
		if (state.getDestroySpeed(mc.level, pos) < 0) return false;
		return mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= reach() * reach();
	}

	/**
	 * Advances breaking of the block (starts or continues). Call every tick until it returns false or the block is
	 * gone. Returns whether the game mode accepted the action.
	 */
	public static boolean breakBlock(BlockPos pos, Direction face, boolean swing) {
		if (mc.gameMode == null || mc.player == null) return false;
		boolean result = mc.gameMode.isDestroying() ? mc.gameMode.continueDestroyBlock(pos, face) : mc.gameMode.startDestroyBlock(pos, face);
		swing(InteractionHand.MAIN_HAND, swing);
		return result;
	}

	/** Face of the block that is closest to the player's eyes (good default break face). */
	public static Direction closestFace(BlockPos pos) {
		Vec3 eyes = mc.player.getEyePosition();
		Direction best = Direction.UP;
		double bestDist = Double.MAX_VALUE;
		for (Direction direction : Direction.values()) {
			Vec3 faceCenter = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(direction.getUnitVec3i()).scale(0.5));
			double d = eyes.distanceToSqr(faceCenter);
			if (d < bestDist) {
				bestDist = d;
				best = direction;
			}
		}
		return best;
	}

	/** Raycast from the eyes to the point (blocks only). True if nothing but the target block is hit. */
	public static boolean canSee(BlockPos pos, Vec3 point) {
		Vec3 eyes = mc.player.getEyePosition();
		BlockHitResult hit = mc.level.clip(new ClipContext(eyes, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
	}

	/** Progress (0..1+) the player adds per tick when mining this block with the current item. */
	public static float breakDelta(BlockPos pos) {
		return state(pos).getDestroyProgress(mc.player, mc.level, pos);
	}

	/** Axis aligned box of a full block. */
	public static AABB box(BlockPos pos) {
		return new AABB(pos);
	}
}
