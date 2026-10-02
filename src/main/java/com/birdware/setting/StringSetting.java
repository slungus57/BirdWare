package com.birdware.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Free text value edited via a text field. */
public class StringSetting extends Setting<String> {
	private final int maxLength;

	public StringSetting(String name, String description, String defaultValue) {
		this(name, description, defaultValue, 256);
	}

	public StringSetting(String name, String description, String defaultValue, int maxLength) {
		super(name, description, defaultValue);
		this.maxLength = maxLength;
	}

	public int getMaxLength() {
		return maxLength;
	}

	@Override
	protected String validate(String candidate) {
		String stripped = candidate.replace("\n", " ").replace("\r", "");
		return stripped.length() > maxLength ? stripped.substring(0, maxLength) : stripped;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public boolean fromJson(JsonElement element) {
		if (element == null || !element.isJsonPrimitive()) return false;
		return set(element.getAsString());
	}

	@Override
	public boolean parse(String input) {
		return input != null && set(input);
	}
}
