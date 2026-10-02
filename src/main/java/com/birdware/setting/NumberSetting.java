package com.birdware.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * A bounded numeric value rendered as a slider. Values are clamped to [min, max] and snapped to {@code step}.
 * Use {@link #getInt()} / {@link #getFloat()} for convenience; integer settings simply use a step of 1.
 */
public class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;
	private final int decimals;
	private String unit = "";

	public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
		super(name, description, clampStatic(defaultValue, min, max));
		if (max < min) throw new IllegalArgumentException("max < min for " + name);
		if (step <= 0) throw new IllegalArgumentException("step must be > 0 for " + name);
		this.min = min;
		this.max = max;
		this.step = step;
		this.decimals = Math.max(0, BigDecimal.valueOf(step).stripTrailingZeros().scale());
		this.value = validate(defaultValue);
	}

	/** Suffix shown after the value in the GUI, e.g. "ms", "b/s", "°". */
	public NumberSetting unit(String unit) {
		this.unit = unit == null ? "" : unit;
		return this;
	}

	public String getUnit() {
		return unit;
	}

	@Override
	protected Double validate(Double candidate) {
		if (candidate == null || candidate.isNaN() || candidate.isInfinite()) return null;
		double clamped = clampStatic(candidate, min, max);
		double snapped = min + Math.round((clamped - min) / step) * step;
		snapped = clampStatic(snapped, min, max);
		return BigDecimal.valueOf(snapped).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
	}

	private static double clampStatic(double v, double min, double max) {
		return v < min ? min : Math.min(v, max);
	}

	@Override
	public boolean isDefault() {
		Double d = validate(defaultValue);
		return d != null && d.equals(value);
	}

	@Override
	public void reset() {
		set(defaultValue);
	}

	public double getDouble() {
		return value;
	}

	public float getFloat() {
		return value.floatValue();
	}

	public int getInt() {
		return (int) Math.round(value);
	}

	public long getLong() {
		return Math.round(value);
	}

	public double getMin() {
		return min;
	}

	public double getMax() {
		return max;
	}

	public double getStep() {
		return step;
	}

	public int getDecimals() {
		return decimals;
	}

	/** Value mapped to [0, 1] across the slider range. */
	public double getProgress() {
		return max == min ? 0 : (value - min) / (max - min);
	}

	public void setProgress(double progress) {
		set(min + Math.max(0, Math.min(1, progress)) * (max - min));
	}

	public void increment(boolean up) {
		set(value + (up ? step : -step));
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public boolean fromJson(JsonElement element) {
		if (element == null || !element.isJsonPrimitive()) return false;
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (primitive.isNumber()) return set(primitive.getAsDouble());
		if (primitive.isString()) return parse(primitive.getAsString());
		return false;
	}

	@Override
	public boolean parse(String input) {
		if (input == null) return false;
		try {
			return set(Double.parseDouble(input.trim().replace(unit, "").trim()));
		} catch (NumberFormatException e) {
			return false;
		}
	}

	@Override
	public String getDisplayValue() {
		return format(value) + unit;
	}

	public String format(double v) {
		if (decimals == 0) return Long.toString(Math.round(v));
		return BigDecimal.valueOf(v).setScale(decimals, RoundingMode.HALF_UP).toPlainString();
	}

	@Override
	public List<String> getSuggestions() {
		return List.of(format(min), format(defaultValue), format(max));
	}
}
