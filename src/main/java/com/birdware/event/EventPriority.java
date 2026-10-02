package com.birdware.event;

/** Common listener priorities. Any int is valid; higher runs first. */
public final class EventPriority {
	public static final int HIGHEST = 200;
	public static final int HIGH = 100;
	public static final int NORMAL = 0;
	public static final int LOW = -100;
	public static final int LOWEST = -200;

	private EventPriority() {
	}
}
