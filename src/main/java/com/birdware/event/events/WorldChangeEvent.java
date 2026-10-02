package com.birdware.event.events;

import com.birdware.event.Event;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Posted when {@code mc.level} changes: joining, respawning into another dimension, leaving (new level null).
 * Old/new may be null.
 */
public final class WorldChangeEvent extends Event {
	private final ClientLevel oldLevel;
	private final ClientLevel newLevel;

	public WorldChangeEvent(ClientLevel oldLevel, ClientLevel newLevel) {
		this.oldLevel = oldLevel;
		this.newLevel = newLevel;
	}

	public ClientLevel getOldLevel() {
		return oldLevel;
	}

	public ClientLevel getNewLevel() {
		return newLevel;
	}
}
