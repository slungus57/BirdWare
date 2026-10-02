package com.birdware.util;

/** Game-tick based counter. Call {@link #tick()} once per client tick from the owning module. */
public final class TickTimer {
	private int ticks;

	public void tick() {
		if (ticks < Integer.MAX_VALUE) ticks++;
	}

	public void reset() {
		ticks = 0;
	}

	public int get() {
		return ticks;
	}

	public boolean passed(int amount) {
		return ticks >= amount;
	}

	public boolean passedAndReset(int amount) {
		if (ticks >= amount) {
			ticks = 0;
			return true;
		}
		return false;
	}

	public void expire() {
		ticks = Integer.MAX_VALUE;
	}
}
