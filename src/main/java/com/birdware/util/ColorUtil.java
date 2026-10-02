package com.birdware.util;

/** Packed ARGB color helpers. All colors in BirdWare are 0xAARRGGBB ints. */
public final class ColorUtil {
	private ColorUtil() {
	}

	public static int argb(int a, int r, int g, int b) {
		return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
	}

	public static int argb(float a, float r, float g, float b) {
		return argb(Math.round(a * 255), Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
	}

	public static int alpha(int c) {
		return (c >>> 24) & 0xFF;
	}

	public static int red(int c) {
		return (c >> 16) & 0xFF;
	}

	public static int green(int c) {
		return (c >> 8) & 0xFF;
	}

	public static int blue(int c) {
		return c & 0xFF;
	}

	public static float alphaF(int c) {
		return alpha(c) / 255f;
	}

	public static float redF(int c) {
		return red(c) / 255f;
	}

	public static float greenF(int c) {
		return green(c) / 255f;
	}

	public static float blueF(int c) {
		return blue(c) / 255f;
	}

	public static int withAlpha(int c, int alpha) {
		return (clamp255(alpha) << 24) | (c & 0x00FFFFFF);
	}

	public static int withAlpha(int c, float alpha) {
		return withAlpha(c, Math.round(alpha * 255));
	}

	/** Multiplies the existing alpha by {@code factor} (0..1). Useful for fade animations. */
	public static int fade(int c, float factor) {
		return withAlpha(c, Math.round(alpha(c) * Math.max(0, Math.min(1, factor))));
	}

	/** Linear interpolation between two colors including alpha. */
	public static int lerp(int from, int to, float t) {
		t = Math.max(0, Math.min(1, t));
		return argb(
			Math.round(alpha(from) + (alpha(to) - alpha(from)) * t),
			Math.round(red(from) + (red(to) - red(from)) * t),
			Math.round(green(from) + (green(to) - green(from)) * t),
			Math.round(blue(from) + (blue(to) - blue(from)) * t));
	}

	/** Brightens (factor > 1) or darkens (factor < 1) the RGB channels, keeping alpha. */
	public static int brighten(int c, float factor) {
		return argb(alpha(c), Math.round(red(c) * factor), Math.round(green(c) * factor), Math.round(blue(c) * factor));
	}

	public static int[] toHsv(int c) {
		float[] hsv = rgbToHsv(red(c), green(c), blue(c));
		return new int[]{Math.round(hsv[0] * 360), Math.round(hsv[1] * 100), Math.round(hsv[2] * 100)};
	}

	/** h, s, v in [0,1]. */
	public static float[] rgbToHsv(int r, int g, int b) {
		float rf = r / 255f, gf = g / 255f, bf = b / 255f;
		float max = Math.max(rf, Math.max(gf, bf));
		float min = Math.min(rf, Math.min(gf, bf));
		float delta = max - min;
		float h;
		if (delta == 0) h = 0;
		else if (max == rf) h = ((gf - bf) / delta) % 6f;
		else if (max == gf) h = (bf - rf) / delta + 2f;
		else h = (rf - gf) / delta + 4f;
		h /= 6f;
		if (h < 0) h += 1f;
		float s = max == 0 ? 0 : delta / max;
		return new float[]{h, s, max};
	}

	/** h, s, v in [0,1], alpha 0..255. */
	public static int hsvToArgb(float h, float s, float v, int alpha) {
		h = h - (float) Math.floor(h);
		s = Math.max(0, Math.min(1, s));
		v = Math.max(0, Math.min(1, v));
		float c = v * s;
		float x = c * (1 - Math.abs((h * 6f) % 2f - 1));
		float m = v - c;
		float r, g, b;
		int sector = (int) (h * 6f);
		switch (sector) {
			case 0 -> {
				r = c;
				g = x;
				b = 0;
			}
			case 1 -> {
				r = x;
				g = c;
				b = 0;
			}
			case 2 -> {
				r = 0;
				g = c;
				b = x;
			}
			case 3 -> {
				r = 0;
				g = x;
				b = c;
			}
			case 4 -> {
				r = x;
				g = 0;
				b = c;
			}
			default -> {
				r = c;
				g = 0;
				b = x;
			}
		}
		return argb(alpha, Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
	}

	/** Seconds for one full hue cycle of rainbow colors. */
	public static volatile float rainbowSeconds = 6f;

	/**
	 * Hue-cycling color that keeps the saturation, brightness and alpha of {@code base} (a fully desaturated base gets
	 * a vivid default saturation so rainbow is always visible).
	 */
	public static int rainbow(int base, long phaseOffsetMs) {
		float[] hsv = rgbToHsv(red(base), green(base), blue(base));
		float s = hsv[1] < 0.15f ? 0.65f : hsv[1];
		float v = hsv[2] < 0.15f ? 1f : hsv[2];
		long period = Math.max(500, (long) (rainbowSeconds * 1000));
		float hue = ((System.currentTimeMillis() + phaseOffsetMs) % period) / (float) period;
		return hsvToArgb(hue, s, v, alpha(base));
	}

	/** Green (full) to red (empty) health/durability gradient. */
	public static int healthColor(float fraction) {
		fraction = Math.max(0, Math.min(1, fraction));
		return hsvToArgb(fraction / 3f, 0.8f, 0.95f, 255);
	}

	/** Parses #RGB, #RRGGBB, #AARRGGBB (with or without '#', or 0x). Returns null if invalid. */
	public static Integer parseHex(String s) {
		if (s == null) return null;
		String hex = s.trim();
		if (hex.startsWith("#")) hex = hex.substring(1);
		else if (hex.startsWith("0x") || hex.startsWith("0X")) hex = hex.substring(2);
		try {
			return switch (hex.length()) {
				case 3 -> {
					int r = Integer.parseInt(hex.substring(0, 1), 16) * 17;
					int g = Integer.parseInt(hex.substring(1, 2), 16) * 17;
					int b = Integer.parseInt(hex.substring(2, 3), 16) * 17;
					yield argb(255, r, g, b);
				}
				case 6 -> 0xFF000000 | Integer.parseInt(hex, 16);
				case 8 -> (int) Long.parseLong(hex, 16);
				default -> null;
			};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static String toHex(int argb, boolean includeAlpha) {
		return includeAlpha ? String.format("#%08X", argb) : String.format("#%06X", argb & 0xFFFFFF);
	}

	private static int clamp255(int v) {
		return v < 0 ? 0 : Math.min(255, v);
	}
}
