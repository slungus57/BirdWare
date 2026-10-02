package com.birdware.event.events;

import com.birdware.event.CancellableEvent;

/** Posted before a chat message (not a /command) typed by the user is sent. Cancel to swallow it. */
public final class ChatSendEvent extends CancellableEvent {
	private String message;

	public ChatSendEvent(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
