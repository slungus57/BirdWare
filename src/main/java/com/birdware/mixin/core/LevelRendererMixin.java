package com.birdware.mixin.core;

import com.birdware.render.Projection;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the exact view/projection matrices of each frame for {@link Projection}. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Inject(method = "renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
		at = @At("HEAD"))
	private void birdware$captureMatrices(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline,
										  Camera camera, Matrix4f view, Matrix4f projection, Matrix4f cullProjection,
										  GpuBufferSlice fog, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
		Projection.update(camera.position(), view, projection);
	}
}
