package com.birdware.event.events;

import com.birdware.event.CancellableEvent;

/**
 * Raw mouse button input, posted before vanilla handles it. {@code action}: 1 press, 0 release.
 * Cancelling prevents vanilla (and screens) from seeing the click.
 */
public final class MouseClickEvent extends CancellableEvent {
	private final int button;
	private final int action;
	private final int modifiers;
	private final boolean screenOpen;

	public MouseClickEvent(int button, int action, int modifiers, boolean screenOpen) {
		this.button = button;
		this.action = action;
		this.modifiers = modifiers;
		this.screenOpen = screenOpen;
	}

	public int getButton() {
		return button;
	}

	public int getAction() {
		return action;
	}

	public int getModifiers() {
		return modifiers;
	}

	public boolean isPress() {
		return action == 1;
	}

	public boolean isRelease() {
		return action == 0;
	}

	public boolean isScreenOpen() {
		return screenOpen;
	}
}
