package com.birdware.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

/** An ordered list of free text entries (e.g. spammer messages, chat filters). */
public class StringListSetting extends Setting<List<String>> {
	private final int maxEntries;
	private final int maxEntryLength;

	public StringListSetting(String name, String description, List<String> defaults) {
		this(name, description, defaults, 256, 256);
	}

	public StringListSetting(String name, String description, List<String> defaults, int maxEntries, int maxEntryLength) {
		super(name, description, List.copyOf(defaults));
		this.maxEntries = maxEntries;
		this.maxEntryLength = maxEntryLength;
	}

	public int getMaxEntries() {
		return maxEntries;
	}

	public int getMaxEntryLength() {
		return maxEntryLength;
	}

	public void add(String entry) {
		if (entry == null || entry.isBlank()) return;
		List<String> next = new ArrayList<>(value);
		next.add(entry);
		set(next);
	}

	public void remove(int index) {
		if (index < 0 || index >= value.size()) return;
		List<String> next = new ArrayList<>(value);
		next.remove(index);
		set(next);
	}

	public void replace(int index, String entry) {
		if (index < 0 || index >= value.size() || entry == null) return;
		List<String> next = new ArrayList<>(value);
		next.set(index, entry);
		set(next);
	}

	public void clear() {
		set(List.of());
	}

	@Override
	protected List<String> validate(List<String> candidate) {
		List<String> cleaned = new ArrayList<>(Math.min(candidate.size(), maxEntries));
		for (String s : candidate) {
			if (s == null) continue;
			String stripped = s.replace("\n", " ").replace("\r", "");
			if (stripped.isBlank()) continue;
			if (stripped.length() > maxEntryLength) stripped = stripped.substring(0, maxEntryLength);
			cleaned.add(stripped);
			if (cleaned.size() >= maxEntries) break;
		}
		return List.copyOf(cleaned);
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
		List<String> list = new ArrayList<>();
		for (JsonElement e : element.getAsJsonArray()) {
			if (e.isJsonPrimitive()) list.add(e.getAsString());
		}
		return set(list);
	}

	/** "+text" adds, "-index" removes (1-based), "clear" empties, anything else replaces with a '|' separated list. */
	@Override
	public boolean parse(String input) {
		if (input == null) return false;
		String trimmed = input.trim();
		if (trimmed.equalsIgnoreCase("clear")) {
			clear();
			return true;
		}
		if (trimmed.startsWith("+")) {
			add(trimmed.substring(1).trim());
			return true;
		}
		if (trimmed.startsWith("-")) {
			try {
				remove(Integer.parseInt(trimmed.substring(1).trim()) - 1);
				return true;
			} catch (NumberFormatException e) {
				return false;
			}
		}
		List<String> list = new ArrayList<>();
		for (String part : trimmed.split("\\|")) list.add(part.trim());
		return set(list);
	}

	@Override
	public String getDisplayValue() {
		return value.size() + (value.size() == 1 ? " entry" : " entries");
	}

	@Override
	public List<String> getSuggestions() {
		return List.of("+<text>", "-<index>", "clear", "a|b|c");
	}
}
