package com.birdware.util;

/**
 * Exponentially smoothed value that chases a target independent of frame rate (scroll offsets, slider thumbs,
 * health bars, ArrayList positions).
 */
public final class SmoothValue {
	private double value;
	private double target;
	private double speed;
	private long lastUpdate = -1;

	/** @param speed approximate convergence rate per second (higher = snappier). 12-20 feels good for UI. */
	public SmoothValue(double initial, double speed) {
		this.value = initial;
		this.target = initial;
		this.speed = speed;
	}

	public void setTarget(double target) {
		this.target = target;
	}

	public double getTarget() {
		return target;
	}

	public void setSpeed(double speed) {
		this.speed = speed;
	}

	public void snap(double v) {
		this.value = v;
		this.target = v;
		this.lastUpdate = System.nanoTime();
	}

	public double get() {
		long now = System.nanoTime();
		if (lastUpdate < 0) {
			lastUpdate = now;
			return value;
		}
		double dt = Math.min(0.25, (now - lastUpdate) / 1_000_000_000.0);
		lastUpdate = now;
		double multiplier = Animation.speedMultiplier;
		if (multiplier <= 0) {
			value = target;
			return value;
		}
		double factor = 1 - Math.exp(-speed * multiplier * dt);
		value += (target - value) * factor;
		if (Math.abs(target - value) < 1e-3) value = target;
		return value;
	}

	public float getFloat() {
		return (float) get();
	}
}
