package com.birdware.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Rotation math in Minecraft conventions: yaw 0 = +Z (south), yaw increases clockwise; pitch +90 = straight down. */
public final class RotationUtil {
	private static final Minecraft mc = Minecraft.getInstance();

	private RotationUtil() {
	}

	/** {yaw, pitch} needed to look from {@code from} at {@code to}. */
	public static float[] calculate(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double dz = to.z - from.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
		float pitch = (float) -(Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG);
		return new float[]{Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90f, 90f)};
	}

	/** {yaw, pitch} from the local player's eyes to a point. */
	public static float[] toPoint(Vec3 point) {
		return calculate(mc.player.getEyePosition(), point);
	}

	/** Unit look vector for a rotation (same formula as {@code Entity#calculateViewVector}). */
	public static Vec3 direction(float yaw, float pitch) {
		float f = pitch * Mth.DEG_TO_RAD;
		float g = -yaw * Mth.DEG_TO_RAD;
		float h = Mth.cos(g);
		float i = Mth.sin(g);
		float j = Mth.cos(f);
		float k = Mth.sin(f);
		return new Vec3(i * j, -k, h * j);
	}

	/** Combined angular distance between two rotations in degrees. */
	public static float angleBetween(float yaw1, float pitch1, float yaw2, float pitch2) {
		float dy = Mth.wrapDegrees(yaw1 - yaw2);
		float dp = pitch1 - pitch2;
		return (float) Math.sqrt(dy * dy + dp * dp);
	}

	/** Angular distance from the player's current (camera) rotation to the point. */
	public static float angleTo(Vec3 point) {
		float[] r = toPoint(point);
		return angleBetween(mc.player.getYRot(), mc.player.getXRot(), r[0], r[1]);
	}

	/**
	 * Smallest possible rotation step for the current mouse sensitivity (vanilla:
	 * {@code (s * 0.6 + 0.2)^3 * 8 * 0.15}). Rotations made of multiples of this look like real mouse input.
	 */
	public static float getGcd() {
		double sensitivity = mc.options.sensitivity().get() * 0.6 + 0.2;
		return (float) (sensitivity * sensitivity * sensitivity * 8.0 * 0.15);
	}

	/** Point on the box closest to the player's eyes (good default aim point: minimal rotation, always in reach). */
	public static Vec3 closestPoint(AABB box) {
		Vec3 eyes = mc.player.getEyePosition();
		return new Vec3(
			Mth.clamp(eyes.x, box.minX, box.maxX),
			Mth.clamp(eyes.y, box.minY, box.maxY),
			Mth.clamp(eyes.z, box.minZ, box.maxZ));
	}

	/** Squared distance from the player's eyes to the closest point of an entity's hitbox (vanilla reach metric). */
	public static double eyeDistanceSqToBox(Entity entity) {
		return mc.player.getEyePosition().distanceToSqr(closestPoint(entity.getBoundingBox()));
	}

	/** Rotation that moves from (curYaw, curPitch) toward (targetYaw, targetPitch) by at most {@code maxStep} degrees. */
	public static float[] limit(float curYaw, float curPitch, float targetYaw, float targetPitch, float maxStep) {
		float dy = Mth.wrapDegrees(targetYaw - curYaw);
		float dp = targetPitch - curPitch;
		float len = (float) Math.sqrt(dy * dy + dp * dp);
		if (len > maxStep && len > 0) {
			dy *= maxStep / len;
			dp *= maxStep / len;
		}
		return new float[]{curYaw + dy, Mth.clamp(curPitch + dp, -90f, 90f)};
	}
}
