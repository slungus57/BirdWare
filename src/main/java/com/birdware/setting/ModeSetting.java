package com.birdware.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

/** One of a fixed list of string modes, rendered as a dropdown. */
public class ModeSetting extends Setting<String> {
	private final List<String> modes;

	public ModeSetting(String name, String description, String defaultMode, String... modes) {
		super(name, description, defaultMode);
		if (modes.length == 0) throw new IllegalArgumentException("ModeSetting needs at least one mode: " + name);
		this.modes = List.of(modes);
		if (!this.modes.contains(defaultMode)) throw new IllegalArgumentException("Default mode not in list: " + name);
	}

	public List<String> getModes() {
		return modes;
	}

	public boolean is(String mode) {
		return value.equalsIgnoreCase(mode);
	}

	public int getIndex() {
		return modes.indexOf(value);
	}

	public void cycle(boolean forward) {
		int i = getIndex();
		int n = modes.size();
		set(modes.get(((forward ? i + 1 : i - 1) % n + n) % n));
	}

	@Override
	protected String validate(String candidate) {
		for (String mode : modes) {
			if (mode.equalsIgnoreCase(candidate)) return mode;
		}
		return null;
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
		return input != null && set(input.trim());
	}

	@Override
	public List<String> getSuggestions() {
		return modes;
	}
}
