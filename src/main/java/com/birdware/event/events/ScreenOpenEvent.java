package com.birdware.event.events;

import com.birdware.event.CancellableEvent;
import net.minecraft.client.gui.screens.Screen;

/** Posted before {@code Minecraft#setScreen} changes the screen. {@code screen} may be null (closing). */
public final class ScreenOpenEvent extends CancellableEvent {
	private final Screen screen;

	public ScreenOpenEvent(Screen screen) {
		this.screen = screen;
	}

	public Screen getScreen() {
		return screen;
	}
}
