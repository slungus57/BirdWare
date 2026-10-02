package com.birdware.module.modules.render.esp;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Render types used by Chams to replace a living entity's body render type.
 *
 * <ul>
 *     <li>{@link #seeThrough(Identifier)}: a clone of vanilla's {@code entity_translucent} pipeline with the depth test
 *     disabled, so the body is drawn on top of terrain. It is translucent (blend set), which makes vanilla queue it as
 *     a translucent model submit drawn after every opaque model. {@code AFFECTS_OUTLINE} keeps the glow outline
 *     working on chams'd entities.</li>
 *     <li>{@link #tinted(Identifier)}: vanilla {@code entity_translucent} (normal depth test) for "Through Walls" off,
 *     so the tint color's alpha is honoured while the entity stays occluded normally.</li>
 * </ul>
 * The tint itself is the vertex color passed to {@code submitModel}; the entity shader multiplies it with the
 * texture, so chams are texture-colored (skin detail stays visible under the tint).
 */
public final class ChamsRenderTypes {
	private static final int MAX_CACHED_TEXTURES = 512;

	/** Entity pipeline without depth testing or depth writes (a no-depth-test pass cannot write depth in GL). */
	public static final RenderPipeline SEE_THROUGH_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("birdware", "pipeline/chams_see_through"))
			.withShaderDefine("ALPHA_CUTOUT", 0.1F)
			.withShaderDefine("PER_FACE_LIGHTING")
			.withSampler("Sampler1")
			.withBlend(BlendFunction.TRANSLUCENT)
			.withCull(false)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.build());

	/**
	 * RenderType uses identity equality and every distinct type is its own batch, so one instance per texture is
	 * cached. Player skins make the key space open-ended on big servers, hence the LRU bound (a RenderType holds no GPU
	 * resources, evicting one is free).
	 */
	private static final Map<Identifier, RenderType> SEE_THROUGH = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Identifier, RenderType> eldest) {
			return size() > MAX_CACHED_TEXTURES;
		}
	};

	private ChamsRenderTypes() {
	}

	/** Forces class initialisation so the pipeline is registered before the first resource reload. */
	public static void init() {
	}

	/** Body render type drawn through walls. Render thread only. */
	public static RenderType seeThrough(Identifier texture) {
		RenderType type = SEE_THROUGH.get(texture);
		if (type == null) {
			type = RenderType.create("birdware_chams_see_through", RenderSetup.builder(SEE_THROUGH_PIPELINE)
				.withTexture("Sampler0", texture)
				.useLightmap()
				.useOverlay()
				.sortOnUpload()
				.setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
				.createRenderSetup());
			SEE_THROUGH.put(texture, type);
		}
		return type;
	}

	/** Translucent but normally depth tested body render type (vanilla memoizes it). */
	public static RenderType tinted(Identifier texture) {
		return RenderTypes.entityTranslucent(texture);
	}
}
