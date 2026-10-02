package com.birdware.gui.theme;

import com.birdware.util.ColorUtil;
import com.google.gson.JsonObject;

/**
 * Immutable snapshot of every visual token used by the ClickGUI, HUD and notifications. Produced by
 * {@link ThemeManager} from the Theme module's settings; read it fresh each frame via {@code ThemeManager.get()}.
 *
 * @param accent          primary accent (enabled modules, slider fills, focus rings)
 * @param accent2         secondary accent (gradients, highlights)
 * @param text            primary text
 * @param textDim         secondary text (descriptions, values)
 * @param background      full-screen backdrop behind the GUI
 * @param panel           panel body
 * @param header          panel header / category bar
 * @param border          hairline borders and separators
 * @param hover           hover overlay
 * @param enabled         background tint of enabled module rows
 * @param disabled        text of disabled modules
 * @param success         positive state color (notifications)
 * @param warning         warning color
 * @param error           error color
 * @param opacity         multiplier for panel/background alpha (0.2 - 1)
 * @param radius          corner radius in GUI pixels
 * @param shadow          drop shadow strength 0 (off) - 1
 * @param animationSpeed  animation speed multiplier (0 disables animations)
 * @param accentGradient  draw accent as an accent→accent2 gradient where supported
 */
public record Theme(
	int accent, int accent2, int text, int textDim, int background, int panel, int header, int border, int hover,
	int enabled, int disabled, int success, int warning, int error,
	float opacity, float radius, float shadow, float animationSpeed, boolean accentGradient
) {
	public static final Theme BIRDWARE = new Theme(
		0xFF4FC3F7, 0xFF7C4DFF, 0xFFF2F4F8, 0xFF9AA3B5, 0x8C05070C, 0xF0141821, 0xFF1B2030, 0x33FFFFFF, 0x14FFFFFF,
		0x334FC3F7, 0xFFB4BCCB, 0xFF4ADE80, 0xFFFACC15, 0xFFF87171,
		0.94f, 6f, 0.6f, 1f, true);

	/** Applies {@link #opacity} to a color's alpha. */
	public int withOpacity(int color) {
		return ColorUtil.fade(color, opacity);
	}

	/** Accent color at {@code t} in [0,1] along the accent gradient (or flat accent). */
	public int accentAt(float t) {
		return accentGradient ? ColorUtil.lerp(accent, accent2, t) : accent;
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("accent", ColorUtil.toHex(accent, true));
		o.addProperty("accent2", ColorUtil.toHex(accent2, true));
		o.addProperty("text", ColorUtil.toHex(text, true));
		o.addProperty("textDim", ColorUtil.toHex(textDim, true));
		o.addProperty("background", ColorUtil.toHex(background, true));
		o.addProperty("panel", ColorUtil.toHex(panel, true));
		o.addProperty("header", ColorUtil.toHex(header, true));
		o.addProperty("border", ColorUtil.toHex(border, true));
		o.addProperty("hover", ColorUtil.toHex(hover, true));
		o.addProperty("enabled", ColorUtil.toHex(enabled, true));
		o.addProperty("disabled", ColorUtil.toHex(disabled, true));
		o.addProperty("success", ColorUtil.toHex(success, true));
		o.addProperty("warning", ColorUtil.toHex(warning, true));
		o.addProperty("error", ColorUtil.toHex(error, true));
		o.addProperty("opacity", opacity);
		o.addProperty("radius", radius);
		o.addProperty("shadow", shadow);
		o.addProperty("animationSpeed", animationSpeed);
		o.addProperty("accentGradient", accentGradient);
		return o;
	}

	/** Lenient parse: any missing/invalid field falls back to {@code fallback}'s value. */
	public static Theme fromJson(JsonObject o, Theme fallback) {
		return new Theme(
			color(o, "accent", fallback.accent), color(o, "accent2", fallback.accent2), color(o, "text", fallback.text),
			color(o, "textDim", fallback.textDim), color(o, "background", fallback.background), color(o, "panel", fallback.panel),
			color(o, "header", fallback.header), color(o, "border", fallback.border), color(o, "hover", fallback.hover),
			color(o, "enabled", fallback.enabled), color(o, "disabled", fallback.disabled), color(o, "success", fallback.success),
			color(o, "warning", fallback.warning), color(o, "error", fallback.error),
			num(o, "opacity", fallback.opacity, 0.2f, 1f), num(o, "radius", fallback.radius, 0f, 12f),
			num(o, "shadow", fallback.shadow, 0f, 1f), num(o, "animationSpeed", fallback.animationSpeed, 0f, 3f),
			bool(o, "accentGradient", fallback.accentGradient));
	}

	private static int color(JsonObject o, String key, int fallback) {
		try {
			Integer parsed = o.has(key) ? ColorUtil.parseHex(o.get(key).getAsString()) : null;
			return parsed != null ? parsed : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static float num(JsonObject o, String key, float fallback, float min, float max) {
		try {
			if (!o.has(key)) return fallback;
			float v = o.get(key).getAsFloat();
			return Float.isFinite(v) ? Math.max(min, Math.min(max, v)) : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static boolean bool(JsonObject o, String key, boolean fallback) {
		try {
			return o.has(key) ? o.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}
}
