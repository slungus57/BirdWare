package com.birdware.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import java.util.List;

/**
 * A clickable action in the settings list (e.g. "Clear logout spots"). It has no persistent value: it is skipped by the
 * config system, and {@code .bw set <module> <button> run} triggers it.
 */
public class ButtonSetting extends Setting<Boolean> {
	private final String label;
	private final Runnable action;

	public ButtonSetting(String name, String description, String label, Runnable action) {
		super(name, description, Boolean.FALSE);
		this.label = label;
		this.action = action;
	}

	public String getLabel() {
		return label;
	}

	public void press() {
		action.run();
	}

	@Override
	public boolean set(Boolean newValue) {
		return true;
	}

	@Override
	public boolean isDefault() {
		return true;
	}

	@Override
	public JsonElement toJson() {
		return JsonNull.INSTANCE;
	}

	@Override
	public boolean fromJson(JsonElement element) {
		return true;
	}

	@Override
	public boolean parse(String input) {
		press();
		return true;
	}

	@Override
	public String getDisplayValue() {
		return label;
	}

	@Override
	public List<String> getSuggestions() {
		return List.of("run");
	}
}
