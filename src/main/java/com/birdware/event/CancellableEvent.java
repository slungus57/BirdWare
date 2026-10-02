package com.birdware.event;

/**
 * An event whose default behaviour can be suppressed by a listener.
 * Listeners further down the priority chain still receive cancelled events and can inspect
 * {@link #isCancelled()}; use {@link Subscribe#receiveCancelled()} to opt out of that.
 */
public abstract class CancellableEvent extends Event {
	private boolean cancelled;

	public boolean isCancelled() {
		return cancelled;
	}

	public void setCancelled(boolean cancelled) {
		this.cancelled = cancelled;
	}

	public void cancel() {
		this.cancelled = true;
	}

	/** Clears the cancelled flag so a pooled instance can be re-posted. */
	protected void resetCancelled() {
		this.cancelled = false;
	}
}
