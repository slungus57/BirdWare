package com.birdware.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * World to GUI-space projection for 2D overlays (nametags, 2D ESP, waypoint labels). Matrices are captured at the
 * start of each level render, so projections made in the HUD pass of the same frame line up exactly with the world
 * (including view bobbing and FOV effects). Render thread only.
 */
public final class Projection {
	private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
	private static final Vector4f TEMP = new Vector4f();
	private static Vec3 camera = Vec3.ZERO;
	private static boolean valid;

	private Projection() {
	}

	/** Called by the LevelRenderer mixin each frame. */
	public static void update(Vec3 cameraPos, Matrix4fc view, Matrix4fc projection) {
		camera = cameraPos;
		VIEW_PROJECTION.set(projection).mul(view);
		valid = true;
	}

	public static Vec3 getCameraPos() {
		return camera;
	}

	/**
	 * Projects a world position to GUI-scaled screen coordinates.
	 * @param out receives x, y (GUI units) and z (NDC depth); may be reused to avoid allocation
	 * @return false when the point is behind the camera or no frame has been rendered yet
	 */
	public static boolean toScreen(double x, double y, double z, Vector3f out) {
		if (!valid) return false;
		TEMP.set((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z), 1f);
		VIEW_PROJECTION.transform(TEMP);
		if (TEMP.w <= 0.0001f) return false;
		float ndcX = TEMP.x / TEMP.w;
		float ndcY = TEMP.y / TEMP.w;
		var window = Minecraft.getInstance().getWindow();
		float guiWidth = window.getWidth() / (float) window.getGuiScale();
		float guiHeight = window.getHeight() / (float) window.getGuiScale();
		out.set((ndcX * 0.5f + 0.5f) * guiWidth, (0.5f - ndcY * 0.5f) * guiHeight, TEMP.z / TEMP.w);
		return true;
	}

	public static boolean toScreen(Vec3 pos, Vector3f out) {
		return toScreen(pos.x, pos.y, pos.z, out);
	}

	/** True if the projected point lies inside the GUI area (with a margin). */
	public static boolean isOnScreen(Vector3f projected, float margin) {
		var window = Minecraft.getInstance().getWindow();
		return projected.x >= -margin && projected.y >= -margin
			&& projected.x <= window.getGuiScaledWidth() + margin && projected.y <= window.getGuiScaledHeight() + margin;
	}
}
