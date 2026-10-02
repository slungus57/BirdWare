package com.birdware.event.events;

import com.birdware.event.Event;

/**
 * Client tick while in a world: only posted when {@code mc.player} and {@code mc.level} are non-null, so listeners
 * need no null checks for those two. Pre runs at the start of {@code Minecraft#tick}, Post at the end.
 * Instances are reused; do not store them.
 */
public abstract class TickEvent extends Event {
	public static final class Pre extends TickEvent {
		public static final Pre INSTANCE = new Pre();

		private Pre() {
		}
	}

	public static final class Post extends TickEvent {
		public static final Post INSTANCE = new Post();

		private Post() {
		}
	}
}
