package com.birdware.module.modules.render.esp;

/**
 * Lazily filled tables of the short numeric strings render overlays print every frame ("12m", "18.5", "x64"), so
 * labels do not allocate a new String per entity per frame. Values outside the tables fall back to normal formatting.
 * Render/client thread only.
 */
public final class TextCache {
	private static final String[] METERS = new String[2049];
	private static final String[] INTEGERS = new String[4097];
	private static final String[] TENTHS = new String[10001];
	private static final String[] COUNTS = new String[4097];
	private static final String[] PERCENT = new String[101];
	private static final String[] PING = new String[2001];

	private TextCache() {
	}

	/** Rounded whole meters, e.g. {@code "37m"}. */
	public static String meters(double distance) {
		int m = (int) Math.round(Math.max(0, distance));
		if (m >= METERS.length) return m + "m";
		String s = METERS[m];
		if (s == null) METERS[m] = s = m + "m";
		return s;
	}

	/** Non-negative integer as text. */
	public static String integer(int value) {
		if (value < 0 || value >= INTEGERS.length) return Integer.toString(value);
		String s = INTEGERS[value];
		if (s == null) INTEGERS[value] = s = Integer.toString(value);
		return s;
	}

	/** One decimal ({@code "18.5"}), trailing ".0" dropped ({@code "20"}). */
	public static String tenths(float value) {
		int t = Math.round(Math.max(0, value) * 10f);
		if (t >= TENTHS.length) return Integer.toString(Math.round(value));
		String s = TENTHS[t];
		if (s == null) TENTHS[t] = s = (t % 10 == 0) ? Integer.toString(t / 10) : (t / 10) + "." + (t % 10);
		return s;
	}

	/** Stack count suffix, e.g. {@code "x64"}. */
	public static String count(int count) {
		if (count < 0 || count >= COUNTS.length) return "x" + count;
		String s = COUNTS[count];
		if (s == null) COUNTS[count] = s = "x" + count;
		return s;
	}

	/** Percentage 0..100, e.g. {@code "87%"}. */
	public static String percent(float fraction) {
		int p = Math.round(Math.max(0f, Math.min(1f, fraction)) * 100f);
		String s = PERCENT[p];
		if (s == null) PERCENT[p] = s = p + "%";
		return s;
	}

	/** Latency, e.g. {@code "42ms"}. */
	public static String ping(int ms) {
		if (ms < 0 || ms >= PING.length) return ms + "ms";
		String s = PING[ms];
		if (s == null) PING[ms] = s = ms + "ms";
		return s;
	}
}
