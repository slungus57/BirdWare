package com.birdware.event.events;

import com.birdware.event.Event;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Posted while the in-game HUD renders (above vanilla HUD elements, below chat). Only posted when a level exists and
 * the HUD is not hidden (F1). Coordinates are GUI-scaled. The instance is reused; do not keep references.
 */
public final class Render2DEvent extends Event {
	private GuiGraphics graphics;
	private float partialTick;
	private int screenWidth;
	private int screenHeight;

	public Render2DEvent set(GuiGraphics graphics, float partialTick) {
		this.graphics = graphics;
		this.partialTick = partialTick;
		this.screenWidth = graphics.guiWidth();
		this.screenHeight = graphics.guiHeight();
		return this;
	}

	public GuiGraphics getGraphics() {
		return graphics;
	}

	public float getPartialTick() {
		return partialTick;
	}

	public int getScreenWidth() {
		return screenWidth;
	}

	public int getScreenHeight() {
		return screenHeight;
	}
}
