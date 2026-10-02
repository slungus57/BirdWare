package com.birdware.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

public class BooleanSetting extends Setting<Boolean> {
	public BooleanSetting(String name, String description, boolean defaultValue) {
		super(name, description, defaultValue);
	}

	public boolean isOn() {
		return value;
	}

	public void toggle() {
		set(!value);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public boolean fromJson(JsonElement element) {
		if (element == null || !element.isJsonPrimitive()) return false;
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (primitive.isBoolean()) return set(primitive.getAsBoolean());
		if (primitive.isString()) return parse(primitive.getAsString());
		return false;
	}

	@Override
	public boolean parse(String input) {
		if (input == null) return false;
		switch (input.trim().toLowerCase()) {
			case "true", "on", "yes", "1", "enable", "enabled" -> {
				return set(true);
			}
			case "false", "off", "no", "0", "disable", "disabled" -> {
				return set(false);
			}
			case "toggle" -> {
				return set(!value);
			}
			default -> {
				return false;
			}
		}
	}

	@Override
	public String getDisplayValue() {
		return value ? "On" : "Off";
	}

	@Override
	public List<String> getSuggestions() {
		return List.of("true", "false", "toggle");
	}
}
