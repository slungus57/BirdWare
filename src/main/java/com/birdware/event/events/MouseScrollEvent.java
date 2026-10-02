package com.birdware.event.events;

import com.birdware.event.CancellableEvent;

/** Mouse wheel input before vanilla handles it (hotbar scrolling, zoom). Cancelling suppresses vanilla handling. */
public final class MouseScrollEvent extends CancellableEvent {
	private final double horizontal;
	private final double vertical;
	private final boolean screenOpen;

	public MouseScrollEvent(double horizontal, double vertical, boolean screenOpen) {
		this.horizontal = horizontal;
		this.vertical = vertical;
		this.screenOpen = screenOpen;
	}

	public double getHorizontal() {
		return horizontal;
	}

	public double getVertical() {
		return vertical;
	}

	public boolean isScreenOpen() {
		return screenOpen;
	}
}
