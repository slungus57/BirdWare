package com.birdware.event.events;

import com.birdware.event.CancellableEvent;
import net.minecraft.network.chat.Component;

/**
 * Posted on the client thread before a message is added to the chat HUD. The message may be replaced
 * ({@link #setMessage}) or the event cancelled to hide it.
 */
public final class ChatReceiveEvent extends CancellableEvent {
	private Component message;

	public ChatReceiveEvent(Component message) {
		this.message = message;
	}

	public Component getMessage() {
		return message;
	}

	public void setMessage(Component message) {
		this.message = message;
	}
}
