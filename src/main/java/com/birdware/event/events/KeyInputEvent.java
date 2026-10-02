package com.birdware.event.events;

import com.birdware.event.CancellableEvent;

/**
 * Raw keyboard input, posted before vanilla handles it. {@code action} is GLFW_PRESS (1), GLFW_RELEASE (0) or
 * GLFW_REPEAT (2). {@code screenOpen} tells whether a Screen (chat, inventory, ClickGUI) will receive the key.
 * Cancelling prevents vanilla (and screens) from seeing the key.
 */
public final class KeyInputEvent extends CancellableEvent {
	private final int key;
	private final int scancode;
	private final int action;
	private final int modifiers;
	private final boolean screenOpen;

	public KeyInputEvent(int key, int scancode, int action, int modifiers, boolean screenOpen) {
		this.key = key;
		this.scancode = scancode;
		this.action = action;
		this.modifiers = modifiers;
		this.screenOpen = screenOpen;
	}

	public int getKey() {
		return key;
	}

	public int getScancode() {
		return scancode;
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

	public boolean isRepeat() {
		return action == 2;
	}

	public boolean isScreenOpen() {
		return screenOpen;
	}
}
