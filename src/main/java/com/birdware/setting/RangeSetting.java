package com.birdware.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A min/max pair within fixed bounds, rendered as a dual-thumb slider (e.g. CPS 9-13, delay 40-80 ms).
 */
public class RangeSetting extends Setting<RangeSetting.Range> {
	public record Range(double low, double high) {
		public double random() {
			if (high <= low) return low;
			return ThreadLocalRandom.current().nextDouble(low, high);
		}

		public boolean contains(double v) {
			return v >= low && v <= high;
		}
	}

	private final double min;
	private final double max;
	private final double step;
	private final int decimals;
	private String unit = "";

	public RangeSetting(String name, String description, double defaultLow, double defaultHigh, double min, double max, double step) {
		super(name, description, new Range(defaultLow, defaultHigh));
		this.min = min;
		this.max = max;
		this.step = step;
		this.decimals = Math.max(0, BigDecimal.valueOf(step).stripTrailingZeros().scale());
		this.value = validate(this.value);
	}

	public RangeSetting unit(String unit) {
		this.unit = unit == null ? "" : unit;
		return this;
	}

	public String getUnit() {
		return unit;
	}

	private double snap(double v) {
		double clamped = Math.max(min, Math.min(max, v));
		double snapped = min + Math.round((clamped - min) / step) * step;
		snapped = Math.max(min, Math.min(max, snapped));
		return BigDecimal.valueOf(snapped).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
	}

	@Override
	protected Range validate(Range candidate) {
		if (Double.isNaN(candidate.low()) || Double.isNaN(candidate.high())) return null;
		double low = snap(Math.min(candidate.low(), candidate.high()));
		double high = snap(Math.max(candidate.low(), candidate.high()));
		return new Range(low, high);
	}

	public double getLow() {
		return value.low();
	}

	public double getHigh() {
		return value.high();
	}

	public void setLow(double low) {
		set(new Range(Math.min(low, value.high()), value.high()));
	}

	public void setHigh(double high) {
		set(new Range(value.low(), Math.max(high, value.low())));
	}

	/** Uniformly random value inside the current range. */
	public double random() {
		return value.random();
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

	public String format(double v) {
		if (decimals == 0) return Long.toString(Math.round(v));
		return BigDecimal.valueOf(v).setScale(decimals, RoundingMode.HALF_UP).toPlainString();
	}

	@Override
	public JsonElement toJson() {
		JsonArray array = new JsonArray();
		array.add(value.low());
		array.add(value.high());
		return array;
	}

	@Override
	public boolean fromJson(JsonElement element) {
		try {
			if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != 2) return false;
			JsonArray array = element.getAsJsonArray();
			return set(new Range(array.get(0).getAsDouble(), array.get(1).getAsDouble()));
		} catch (RuntimeException e) {
			return false;
		}
	}

	@Override
	public boolean parse(String input) {
		if (input == null) return false;
		String[] parts = input.trim().split("\\s*[-,: ]\\s*");
		try {
			if (parts.length == 1) {
				double v = Double.parseDouble(parts[0]);
				return set(new Range(v, v));
			}
			if (parts.length == 2) return set(new Range(Double.parseDouble(parts[0]), Double.parseDouble(parts[1])));
		} catch (NumberFormatException ignored) {
		}
		return false;
	}

	@Override
	public String getDisplayValue() {
		return format(value.low()) + " - " + format(value.high()) + unit;
	}

	@Override
	public List<String> getSuggestions() {
		return List.of(format(defaultValue.low()) + "-" + format(defaultValue.high()));
	}
}
