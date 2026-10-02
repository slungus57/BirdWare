package com.birdware.event.events;

import com.birdware.event.Event;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;

/**
 * Posted once per frame after all terrain and entities are drawn (Fabric END_MAIN). Draw with
 * {@link com.birdware.render.Render3D} using <b>world coordinates</b>; it converts to camera-relative space and flushes
 * everything (fog disabled) after the event. Instance is reused.
 */
public final class Render3DEvent extends Event {
	public static final Render3DEvent INSTANCE = new Render3DEvent();

	private PoseStack matrices;
	private float partialTick;
	private Vec3 cameraPos;

	private Render3DEvent() {
	}

	public Render3DEvent set(PoseStack matrices, float partialTick, Vec3 cameraPos) {
		this.matrices = matrices;
		this.partialTick = partialTick;
		this.cameraPos = cameraPos;
		return this;
	}

	public PoseStack getMatrices() {
		return matrices;
	}

	public float getPartialTick() {
		return partialTick;
	}

	public Vec3 getCameraPos() {
		return cameraPos;
	}
}
