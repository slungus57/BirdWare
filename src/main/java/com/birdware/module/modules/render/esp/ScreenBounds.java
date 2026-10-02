package com.birdware.module.modules.render.esp;

import com.birdware.render.Projection;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Screen-space rectangle of an entity's frame-interpolated bounding box (all 8 corners projected with
 * {@link Projection}). One reusable instance per module: no allocation per entity. Only valid during the
 * {@link com.birdware.event.events.Render2DEvent} of the frame (GUI coordinates).
 */
public final class ScreenBounds {
	public float minX;
	public float minY;
	public float maxX;
	public float maxY;
	/** Closest NDC depth of the projected corners (smaller is nearer). */
	public float depth;
	private final Vector3f temp = new Vector3f();

	/**
	 * Projects the entity's interpolated box, grown by {@code expand} blocks on every side.
	 * @return false when any corner is behind the camera (the rectangle would be meaningless)
	 */
	public boolean project(Entity entity, float partialTick, double expand) {
		double dx = Mth.lerp(partialTick, entity.xOld, entity.getX()) - entity.getX();
		double dy = Mth.lerp(partialTick, entity.yOld, entity.getY()) - entity.getY();
		double dz = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - entity.getZ();
		AABB box = entity.getBoundingBox();
		return project(box.minX + dx - expand, box.minY + dy - expand, box.minZ + dz - expand,
			box.maxX + dx + expand, box.maxY + dy + expand, box.maxZ + dz + expand);
	}

	/** Projects an arbitrary world-space box. */
	public boolean project(double x0, double y0, double z0, double x1, double y1, double z1) {
		minX = Float.MAX_VALUE;
		minY = Float.MAX_VALUE;
		maxX = -Float.MAX_VALUE;
		maxY = -Float.MAX_VALUE;
		depth = Float.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			double x = (i & 1) == 0 ? x0 : x1;
			double y = (i & 2) == 0 ? y0 : y1;
			double z = (i & 4) == 0 ? z0 : z1;
			if (!Projection.toScreen(x, y, z, temp)) return false;
			if (temp.x < minX) minX = temp.x;
			if (temp.y < minY) minY = temp.y;
			if (temp.x > maxX) maxX = temp.x;
			if (temp.y > maxY) maxY = temp.y;
			if (temp.z < depth) depth = temp.z;
		}
		return true;
	}

	/** True if the rectangle overlaps the visible GUI area (with a margin in GUI units). */
	public boolean isOnScreen(float guiWidth, float guiHeight, float margin) {
		return maxX >= -margin && maxY >= -margin && minX <= guiWidth + margin && minY <= guiHeight + margin;
	}

	public float width() {
		return maxX - minX;
	}

	public float height() {
		return maxY - minY;
	}

	public float centerX() {
		return (minX + maxX) * 0.5f;
	}
}
