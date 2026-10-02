package com.birdware.util;

/** Millisecond stopwatch for delays and throttling. Uses a monotonic clock. */
public final class MsTimer {
	private long start = System.nanoTime();

	public void reset() {
		start = System.nanoTime();
	}

	public long elapsed() {
		return (System.nanoTime() - start) / 1_000_000L;
	}

	public boolean passed(double ms) {
		return elapsed() >= ms;
	}

	/** Returns true and resets when {@code ms} has elapsed. */
	public boolean passedAndReset(double ms) {
		if (passed(ms)) {
			reset();
			return true;
		}
		return false;
	}

	/** Moves the start into the past so the next {@link #passed} check succeeds immediately. */
	public void expire() {
		start = System.nanoTime() - 3_600_000_000_000L;
	}
}
