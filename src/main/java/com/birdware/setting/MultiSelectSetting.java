package com.birdware.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Any subset of a fixed list of options (e.g. target types). Rendered as a list of chips/checkboxes. */
public class MultiSelectSetting extends Setting<Set<String>> {
	private final List<String> options;

	public MultiSelectSetting(String name, String description, List<String> options, List<String> defaults) {
		super(name, description, Collections.unmodifiableSet(new LinkedHashSet<>(defaults)));
		this.options = List.copyOf(options);
		for (String d : defaults) {
			if (!options.contains(d)) throw new IllegalArgumentException("Default '" + d + "' not an option of " + name);
		}
	}

	public List<String> getOptions() {
		return options;
	}

	public boolean isSelected(String option) {
		return value.contains(option);
	}

	public void setSelected(String option, boolean selected) {
		String canonical = canonical(option);
		if (canonical == null) return;
		LinkedHashSet<String> next = new LinkedHashSet<>(value);
		if (selected) next.add(canonical);
		else next.remove(canonical);
		set(next);
	}

	public void toggle(String option) {
		setSelected(option, !isSelected(canonical(option)));
	}

	private String canonical(String option) {
		if (option == null) return null;
		for (String o : options) {
			if (o.equalsIgnoreCase(option.trim()) || Setting.idOf(o).equals(Setting.idOf(option))) return o;
		}
		return null;
	}

	@Override
	protected Set<String> validate(Set<String> candidate) {
		// Keep declaration order and drop unknown entries.
		LinkedHashSet<String> ordered = new LinkedHashSet<>();
		for (String option : options) {
			for (String c : candidate) {
				if (option.equalsIgnoreCase(c)) {
					ordered.add(option);
					break;
				}
			}
		}
		return Collections.unmodifiableSet(ordered);
	}

	@Override
	public JsonElement toJson() {
		JsonArray array = new JsonArray();
		for (String s : value) array.add(s);
		return array;
	}

	@Override
	public boolean fromJson(JsonElement element) {
		if (element == null || !element.isJsonArray()) return false;
		LinkedHashSet<String> set = new LinkedHashSet<>();
		for (JsonElement e : element.getAsJsonArray()) {
			if (e.isJsonPrimitive()) set.add(e.getAsString());
		}
		return set(set);
	}

	/** Accepts "a,b,c" (replace), "+a" (add), "-a" (remove) or a single option name (toggle). */
	@Override
	public boolean parse(String input) {
		if (input == null || input.isBlank()) return false;
		String trimmed = input.trim();
		if (trimmed.startsWith("+") || trimmed.startsWith("-")) {
			String option = canonical(trimmed.substring(1));
			if (option == null) return false;
			setSelected(option, trimmed.charAt(0) == '+');
			return true;
		}
		if (trimmed.contains(",")) {
			LinkedHashSet<String> set = new LinkedHashSet<>();
			for (String part : trimmed.split(",")) {
				String option = canonical(part);
				if (option == null) return false;
				set.add(option);
			}
			return set(set);
		}
		String option = canonical(trimmed);
		if (option == null) return false;
		toggle(option);
		return true;
	}

	@Override
	public String getDisplayValue() {
		if (value.isEmpty()) return "None";
		if (value.size() == options.size()) return "All";
		return String.join(", ", value);
	}

	@Override
	public List<String> getSuggestions() {
		List<String> list = new ArrayList<>();
		for (String o : options) list.add(Setting.idOf(o));
		return list;
	}
}
