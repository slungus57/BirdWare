package com.birdware.util;

/**
 * Time based 0..1 animation driven by a target direction. Frame-rate independent: progress advances by real elapsed
 * time, so GUI motion looks identical at 30 and 300 FPS. No allocation per frame.
 *
 * <pre>
 * Animation hover = new Animation(150, Easing.CUBIC_OUT);
 * hover.setForward(mouseOver);            // every frame
 * float t = hover.get();                  // eased 0..1
 * </pre>
 */
public final class Animation {
	/** Global multiplier applied to every duration (theme "animation speed"). 0 disables animations. */
	public static volatile double speedMultiplier = 1.0;

	private final Easing easing;
	private double durationMs;
	private double linear;
	private boolean forward;
	private long lastUpdate = -1;

	public Animation(double durationMs, Easing easing) {
		this.durationMs = durationMs;
		this.easing = easing;
	}

	public Animation(double durationMs, Easing easing, boolean startForward) {
		this(durationMs, easing);
		this.forward = startForward;
		this.linear = startForward ? 1 : 0;
	}

	public void setForward(boolean forward) {
		update();
		this.forward = forward;
	}

	public boolean isForward() {
		return forward;
	}

	public void setDuration(double durationMs) {
		this.durationMs = durationMs;
	}

	/** Jumps to the end state of the given direction without animating. */
	public void snap(boolean forward) {
		this.forward = forward;
		this.linear = forward ? 1 : 0;
		this.lastUpdate = System.nanoTime();
	}

	private void update() {
		long now = System.nanoTime();
		if (lastUpdate < 0) {
			lastUpdate = now;
			return;
		}
		double elapsedMs = (now - lastUpdate) / 1_000_000.0;
		lastUpdate = now;
		double effective = durationMs / Math.max(1e-6, speedMultiplier);
		double delta = speedMultiplier <= 0 || effective <= 0 ? 1 : elapsedMs / effective;
		linear = forward ? Math.min(1, linear + delta) : Math.max(0, linear - delta);
	}

	/** Eased progress in [0, 1] (BACK easing may overshoot slightly). */
	public float get() {
		update();
		return (float) easing.apply(linear);
	}

	/** Raw linear progress in [0, 1]. */
	public double getLinear() {
		update();
		return linear;
	}

	public boolean isFinished() {
		update();
		return forward ? linear >= 1 : linear <= 0;
	}

	/** True while fully collapsed (useful to skip rendering hidden elements). */
	public boolean isHidden() {
		update();
		return !forward && linear <= 0;
	}
}
