package com.birdware.render;

import com.birdware.BirdWare;
import com.birdware.event.events.Render3DEvent;
import com.birdware.mixin.core.GameRendererAccessor;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Immediate-mode world overlay drawing. Only valid inside a {@link Render3DEvent} listener.
 *
 * <ul>
 *     <li>All methods take <b>world</b> coordinates; subtraction of the camera position happens in double precision
 *     (no jitter at large coordinates on anarchy servers).</li>
 *     <li>{@code seeThrough} selects the no-depth-test pipelines (ESP through walls).</li>
 *     <li>Geometry is batched into BirdWare's own buffers and drawn once after the event with fog disabled, so no
 *     vanilla render state is touched and nothing leaks into vanilla batches.</li>
 *     <li>Line widths are in framebuffer pixels; {@link #defaultLineWidth()} matches vanilla's outline width.</li>
 * </ul>
 */
public final class Render3D {
	private static final Minecraft mc = Minecraft.getInstance();
	private static MultiBufferSource.BufferSource buffers;
	private static PoseStack.Pose pose;
	private static Vec3 cam = Vec3.ZERO;
	private static float partialTick;
	private static boolean drawing;
	private static final Matrix4f VIEW = new Matrix4f();
	private static final Quaternionf ROTATION = new Quaternionf();
	private static final Vector4f TEMP_A = new Vector4f();
	private static final Vector4f TEMP_B = new Vector4f();

	private Render3D() {
	}

	/** Registers the world render hook. Called once at startup. */
	public static void init() {
		BirdWareRenderTypes.init();
		net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.END_MAIN.register(Render3D::onRender);
	}

	private static MultiBufferSource.BufferSource buffers() {
		if (buffers == null) {
			LinkedHashMap<RenderType, ByteBufferBuilder> fixed = new LinkedHashMap<>();
			// Insertion order = draw order: fills first so outlines sit on top.
			fixed.put(BirdWareRenderTypes.FILLED, new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE));
			fixed.put(BirdWareRenderTypes.FILLED_SEE_THROUGH, new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE));
			fixed.put(BirdWareRenderTypes.LINES, new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE));
			fixed.put(BirdWareRenderTypes.LINES_SEE_THROUGH, new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE));
			buffers = MultiBufferSource.immediateWithBuffers(fixed, new ByteBufferBuilder(RenderType.TRANSIENT_BUFFER_SIZE));
		}
		return buffers;
	}

	private static void onRender(WorldRenderContext context) {
		if (mc.level == null || mc.player == null || BirdWare.get() == null) return;
		if (!BirdWare.get().events().hasListeners(Render3DEvent.class)) return;
		PoseStack matrices = context.matrices();
		cam = context.worldState().cameraRenderState.pos;
		partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Camera camera = mc.gameRenderer.getMainCamera();
		VIEW.identity().rotation(camera.rotation().conjugate(ROTATION));
		matrices.pushPose();
		pose = matrices.last();
		drawing = true;
		try {
			BirdWare.get().events().post(Render3DEvent.INSTANCE.set(matrices, partialTick, cam));
		} finally {
			drawing = false;
			matrices.popPose();
			flush();
		}
	}

	private static void flush() {
		MultiBufferSource.BufferSource source = buffers();
		GpuBufferSlice previousFog = RenderSystem.getShaderFog();
		FogRenderer fogRenderer = ((GameRendererAccessor) mc.gameRenderer).birdware$getFogRenderer();
		RenderSystem.setShaderFog(fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
		try {
			source.endBatch();
		} finally {
			RenderSystem.setShaderFog(previousFog);
		}
	}

	private static VertexConsumer lines(boolean seeThrough) {
		return buffers().getBuffer(seeThrough ? BirdWareRenderTypes.LINES_SEE_THROUGH : BirdWareRenderTypes.LINES);
	}

	private static VertexConsumer quads(boolean seeThrough) {
		return buffers().getBuffer(seeThrough ? BirdWareRenderTypes.FILLED_SEE_THROUGH : BirdWareRenderTypes.FILLED);
	}

	// ------------------------------------------------------------------------------------------------ info

	public static boolean isDrawing() {
		return drawing;
	}

	public static Vec3 cameraPos() {
		return cam;
	}

	public static float partialTick() {
		return partialTick;
	}

	/** Vanilla block-outline width in framebuffer pixels (scales with resolution). */
	public static float defaultLineWidth() {
		return mc.getWindow().getAppropriateLineWidth();
	}

	/** Entity position interpolated for the current frame. */
	public static Vec3 lerpPos(Entity entity) {
		return entity.getPosition(partialTick);
	}

	/** Entity bounding box interpolated for the current frame (world coordinates). */
	public static AABB lerpBox(Entity entity) {
		Vec3 lerped = entity.getPosition(partialTick);
		return entity.getBoundingBox().move(lerped.x - entity.getX(), lerped.y - entity.getY(), lerped.z - entity.getZ());
	}

	// ------------------------------------------------------------------------------------------------ primitives

	/** A world-space line segment. */
	public static void line(double x1, double y1, double z1, double x2, double y2, double z2, int argb, float width, boolean seeThrough) {
		if (!drawing || (argb >>> 24) == 0) return;
		emitLine(lines(seeThrough), (float) (x1 - cam.x), (float) (y1 - cam.y), (float) (z1 - cam.z),
			(float) (x2 - cam.x), (float) (y2 - cam.y), (float) (z2 - cam.z), argb, width);
	}

	public static void line(Vec3 a, Vec3 b, int argb, float width, boolean seeThrough) {
		line(a.x, a.y, a.z, b.x, b.y, b.z, argb, width, seeThrough);
	}

	/** Connected line through all points (trajectories, paths). */
	public static void lineStrip(List<Vec3> points, int argb, float width, boolean seeThrough) {
		for (int i = 1; i < points.size(); i++) line(points.get(i - 1), points.get(i), argb, width, seeThrough);
	}

	private static void emitLine(VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, int argb, float width) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float dz = z2 - z1;
		float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 1.0E-6f) return;
		dx /= length;
		dy /= length;
		dz /= length;
		vc.addVertex(pose, x1, y1, z1).setColor(argb).setNormal(pose, dx, dy, dz).setLineWidth(width);
		vc.addVertex(pose, x2, y2, z2).setColor(argb).setNormal(pose, dx, dy, dz).setLineWidth(width);
	}

	/** Box edges. */
	public static void boxOutline(AABB box, int argb, float width, boolean seeThrough) {
		if (!drawing || (argb >>> 24) == 0) return;
		VertexConsumer vc = lines(seeThrough);
		float x0 = (float) (box.minX - cam.x), y0 = (float) (box.minY - cam.y), z0 = (float) (box.minZ - cam.z);
		float x1 = (float) (box.maxX - cam.x), y1 = (float) (box.maxY - cam.y), z1 = (float) (box.maxZ - cam.z);
		emitLine(vc, x0, y0, z0, x1, y0, z0, argb, width);
		emitLine(vc, x0, y0, z1, x1, y0, z1, argb, width);
		emitLine(vc, x0, y1, z0, x1, y1, z0, argb, width);
		emitLine(vc, x0, y1, z1, x1, y1, z1, argb, width);
		emitLine(vc, x0, y0, z0, x0, y1, z0, argb, width);
		emitLine(vc, x1, y0, z0, x1, y1, z0, argb, width);
		emitLine(vc, x0, y0, z1, x0, y1, z1, argb, width);
		emitLine(vc, x1, y0, z1, x1, y1, z1, argb, width);
		emitLine(vc, x0, y0, z0, x0, y0, z1, argb, width);
		emitLine(vc, x1, y0, z0, x1, y0, z1, argb, width);
		emitLine(vc, x0, y1, z0, x0, y1, z1, argb, width);
		emitLine(vc, x1, y1, z0, x1, y1, z1, argb, width);
	}

	/** Only the 8 corners of a box, each drawn as three short edges ({@code fraction} of the edge length). */
	public static void boxCorners(AABB box, int argb, float width, double fraction, boolean seeThrough) {
		if (!drawing || (argb >>> 24) == 0) return;
		VertexConsumer vc = lines(seeThrough);
		double fx = box.getXsize() * fraction, fy = box.getYsize() * fraction, fz = box.getZsize() * fraction;
		double[] xs = {box.minX, box.maxX};
		double[] ys = {box.minY, box.maxY};
		double[] zs = {box.minZ, box.maxZ};
		for (int ix = 0; ix < 2; ix++) {
			for (int iy = 0; iy < 2; iy++) {
				for (int iz = 0; iz < 2; iz++) {
					double x = xs[ix], y = ys[iy], z = zs[iz];
					double sx = ix == 0 ? fx : -fx, sy = iy == 0 ? fy : -fy, sz = iz == 0 ? fz : -fz;
					float cx = (float) (x - cam.x), cy = (float) (y - cam.y), cz = (float) (z - cam.z);
					emitLine(vc, cx, cy, cz, (float) (x + sx - cam.x), cy, cz, argb, width);
					emitLine(vc, cx, cy, cz, cx, (float) (y + sy - cam.y), cz, argb, width);
					emitLine(vc, cx, cy, cz, cx, cy, (float) (z + sz - cam.z), argb, width);
				}
			}
		}
	}

	/** Translucent filled box (all six faces). */
	public static void boxFilled(AABB box, int argb, boolean seeThrough) {
		if (!drawing || (argb >>> 24) == 0) return;
		VertexConsumer vc = quads(seeThrough);
		float x0 = (float) (box.minX - cam.x), y0 = (float) (box.minY - cam.y), z0 = (float) (box.minZ - cam.z);
		float x1 = (float) (box.maxX - cam.x), y1 = (float) (box.maxY - cam.y), z1 = (float) (box.maxZ - cam.z);
		quad(vc, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(vc, argb, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		quad(vc, argb, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		quad(vc, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(vc, argb, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(vc, argb, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
	}

	/** Only the bottom face of a box (hole ESP, flat markers). */
	public static void boxBottom(AABB box, int argb, boolean seeThrough) {
		if (!drawing || (argb >>> 24) == 0) return;
		float x0 = (float) (box.minX - cam.x), y0 = (float) (box.minY - cam.y), z0 = (float) (box.minZ - cam.z);
		float x1 = (float) (box.maxX - cam.x), z1 = (float) (box.maxZ - cam.z);
		quad(quads(seeThrough), argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
	}

	/** Vertical gradient-free "glow column" used for fades: a filled box whose alpha is taken from argb. */
	public static void box(AABB box, int fillArgb, int outlineArgb, float width, boolean seeThrough) {
		boxFilled(box, fillArgb, seeThrough);
		boxOutline(box, outlineArgb, width, seeThrough);
	}

	public static AABB blockBox(BlockPos pos) {
		return new AABB(pos);
	}

	private static void quad(VertexConsumer vc, int argb, float... v) {
		for (int i = 0; i < 12; i += 3) vc.addVertex(pose, v[i], v[i + 1], v[i + 2]).setColor(argb);
	}

	/** Horizontal circle (waypoint rings, range indicators). */
	public static void circle(Vec3 center, double radius, int segments, int argb, float width, boolean seeThrough) {
		if (!drawing || (argb >>> 24) == 0 || segments < 3) return;
		VertexConsumer vc = lines(seeThrough);
		double prevX = center.x + radius, prevZ = center.z;
		for (int i = 1; i <= segments; i++) {
			double angle = i * (Math.PI * 2 / segments);
			double x = center.x + Math.cos(angle) * radius;
			double z = center.z + Math.sin(angle) * radius;
			emitLine(vc, (float) (prevX - cam.x), (float) (center.y - cam.y), (float) (prevZ - cam.z),
				(float) (x - cam.x), (float) (center.y - cam.y), (float) (z - cam.z), argb, width);
			prevX = x;
			prevZ = z;
		}
	}

	/**
	 * Line from just in front of the camera (crosshair) to a world point. The segment is clipped against the near
	 * plane so targets behind the player still produce a correct line towards the screen edge.
	 */
	public static void tracer(Vec3 target, int argb, float width) {
		if (!drawing || (argb >>> 24) == 0) return;
		Camera camera = mc.gameRenderer.getMainCamera();
		Vector3f forward = new Vector3f(camera.forwardVector());
		float sx = forward.x * 1.5f, sy = forward.y * 1.5f, sz = forward.z * 1.5f;
		float ex = (float) (target.x - cam.x), ey = (float) (target.y - cam.y), ez = (float) (target.z - cam.z);
		TEMP_A.set(sx, sy, sz, 1f).mul(VIEW);
		TEMP_B.set(ex, ey, ez, 1f).mul(VIEW);
		boolean aBehind = TEMP_A.z > -0.05f;
		boolean bBehind = TEMP_B.z > -0.05f;
		if (aBehind && bBehind) return;
		if (bBehind) {
			float t = (-0.05f - TEMP_A.z) / (TEMP_B.z - TEMP_A.z);
			ex = Mth.lerp(t, sx, ex);
			ey = Mth.lerp(t, sy, ey);
			ez = Mth.lerp(t, sz, ez);
		}
		emitLine(lines(true), sx, sy, sz, ex, ey, ez, argb, width);
	}
}
