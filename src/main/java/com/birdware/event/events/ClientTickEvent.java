package com.birdware.event.events;

import com.birdware.event.Event;

/**
 * Client tick posted always, including in menus (player/level may be null). Use {@link TickEvent} for in-game logic.
 */
public abstract class ClientTickEvent extends Event {
	public static final class Pre extends ClientTickEvent {
		public static final Pre INSTANCE = new Pre();

		private Pre() {
		}
	}

	public static final class Post extends ClientTickEvent {
		public static final Post INSTANCE = new Post();

		private Post() {
		}
	}
}
