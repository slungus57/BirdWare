package com.birdware.setting;

import com.birdware.util.ColorUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.List;

/**
 * RGBA color with optional animated "rainbow" (hue cycling) mode. Always read the color through {@link #argb()} so the
 * rainbow mode is honoured.
 */
public class ColorSetting extends Setting<ColorSetting.Value> {
	/**
	 * @param argb    packed 0xAARRGGBB color
	 * @param rainbow cycle the hue over time, keeping saturation/brightness/alpha of {@code argb}
	 */
	public record Value(int argb, boolean rainbow) {
		public int alpha() {
			return (argb >>> 24) & 0xFF;
		}
	}

	private boolean allowAlpha = true;

	public ColorSetting(String name, String description, int defaultArgb) {
		super(name, description, new Value(defaultArgb, false));
	}

	/** Disables the alpha slider in the GUI and forces full opacity. */
	public ColorSetting noAlpha() {
		this.allowAlpha = false;
		set(new Value(value.argb() | 0xFF000000, value.rainbow()));
		return this;
	}

	public boolean allowsAlpha() {
		return allowAlpha;
	}

	@Override
	protected Value validate(Value candidate) {
		if (!allowAlpha) return new Value(candidate.argb() | 0xFF000000, candidate.rainbow());
		return candidate;
	}

	/** Current color with rainbow applied. */
	public int argb() {
		return value.rainbow() ? ColorUtil.rainbow(value.argb(), 0) : value.argb();
	}

	/** Current color with rainbow applied, phase-shifted (for gradients along lists). */
	public int argb(long phaseOffsetMs) {
		return value.rainbow() ? ColorUtil.rainbow(value.argb(), phaseOffsetMs) : value.argb();
	}

	/** Current color with alpha replaced. */
	public int withAlpha(int alpha) {
		return ColorUtil.withAlpha(argb(), alpha);
	}

	public boolean isRainbow() {
		return value.rainbow();
	}

	public void setArgb(int argb) {
		set(new Value(argb, value.rainbow()));
	}

	public void setRainbow(boolean rainbow) {
		set(new Value(value.argb(), rainbow));
	}

	@Override
	public JsonElement toJson() {
		JsonObject object = new JsonObject();
		object.addProperty("color", String.format("#%08X", value.argb()));
		object.addProperty("rainbow", value.rainbow());
		return object;
	}

	@Override
	public boolean fromJson(JsonElement element) {
		try {
			if (element == null) return false;
			if (element.isJsonPrimitive()) {
				JsonPrimitive primitive = element.getAsJsonPrimitive();
				if (primitive.isNumber()) return set(new Value(primitive.getAsInt(), false));
				return parse(primitive.getAsString());
			}
			if (!element.isJsonObject()) return false;
			JsonObject object = element.getAsJsonObject();
			Integer argb = ColorUtil.parseHex(object.has("color") ? object.get("color").getAsString() : null);
			if (argb == null) return false;
			boolean rainbow = object.has("rainbow") && object.get("rainbow").getAsBoolean();
			return set(new Value(argb, rainbow));
		} catch (RuntimeException e) {
			return false;
		}
	}

	@Override
	public boolean parse(String input) {
		if (input == null) return false;
		String trimmed = input.trim();
		if (trimmed.equalsIgnoreCase("rainbow")) return set(new Value(value.argb(), true));
		if (trimmed.equalsIgnoreCase("static")) return set(new Value(value.argb(), false));
		Integer argb = ColorUtil.parseHex(trimmed);
		if (argb == null) {
			String[] parts = trimmed.split("[ ,]+");
			if (parts.length == 3 || parts.length == 4) {
				try {
					int r = Integer.parseInt(parts[0]);
					int g = Integer.parseInt(parts[1]);
					int b = Integer.parseInt(parts[2]);
					int a = parts.length == 4 ? Integer.parseInt(parts[3]) : 255;
					argb = ColorUtil.argb(a, r, g, b);
				} catch (NumberFormatException e) {
					return false;
				}
			}
		}
		return argb != null && set(new Value(argb, false));
	}

	@Override
	public String getDisplayValue() {
		return String.format("#%08X", value.argb()) + (value.rainbow() ? " (rainbow)" : "");
	}

	@Override
	public List<String> getSuggestions() {
		return List.of("#FFFFFFFF", "255 255 255 255", "rainbow", "static");
	}
}
