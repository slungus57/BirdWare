package com.birdware.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Pipelines and render types for world overlays. Pipelines are registered with vanilla so they are precompiled and
 * validated on resource reload. {@link #init()} must run during client initialisation.
 */
public final class BirdWareRenderTypes {
	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("birdware", path);
	}

	public static final RenderPipeline LINES_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation(id("pipeline/lines"))
			.withDepthWrite(false)
			.build());
	public static final RenderPipeline LINES_SEE_THROUGH_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation(id("pipeline/lines_see_through"))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.build());
	public static final RenderPipeline FILLED_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(id("pipeline/filled"))
			.withCull(false)
			.build());
	public static final RenderPipeline FILLED_SEE_THROUGH_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(id("pipeline/filled_see_through"))
			.withCull(false)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.build());

	/** Depth tested lines (hidden behind blocks). */
	public static final RenderType LINES = RenderType.create("birdware_lines",
		RenderSetup.builder(LINES_PIPELINE).setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.bufferSize(RenderType.SMALL_BUFFER_SIZE).createRenderSetup());
	/** Lines drawn through walls. */
	public static final RenderType LINES_SEE_THROUGH = RenderType.create("birdware_lines_see_through",
		RenderSetup.builder(LINES_SEE_THROUGH_PIPELINE).bufferSize(RenderType.SMALL_BUFFER_SIZE).createRenderSetup());
	/** Depth tested translucent quads. */
	public static final RenderType FILLED = RenderType.create("birdware_filled",
		RenderSetup.builder(FILLED_PIPELINE).setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.sortOnUpload().bufferSize(RenderType.SMALL_BUFFER_SIZE).createRenderSetup());
	/** Translucent quads drawn through walls. */
	public static final RenderType FILLED_SEE_THROUGH = RenderType.create("birdware_filled_see_through",
		RenderSetup.builder(FILLED_SEE_THROUGH_PIPELINE).sortOnUpload().bufferSize(RenderType.SMALL_BUFFER_SIZE).createRenderSetup());

	private BirdWareRenderTypes() {
	}

	/** Forces class initialisation (and pipeline registration) before the first resource reload. */
	public static void init() {
	}
}
