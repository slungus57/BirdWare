package com.birdware.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A list of registry entries (blocks, items, entity types) stored as identifiers. Unknown identifiers (e.g. from a
 * newer config) are dropped on load. Lookups use a cached identity set, so {@link #contains(Object)} is O(1) and
 * safe to call per block / per entity.
 */
public class RegistryListSetting<T> extends Setting<List<String>> {
	private final Registry<T> registry;
	private Set<T> resolved = Set.of();

	public RegistryListSetting(String name, String description, Registry<T> registry, List<String> defaults) {
		super(name, description, List.copyOf(defaults));
		this.registry = registry;
		this.value = validate(this.value);
		rebuild();
	}

	public Registry<T> getRegistry() {
		return registry;
	}

	public boolean contains(T entry) {
		return resolved.contains(entry);
	}

	public Set<T> getEntries() {
		return resolved;
	}

	public boolean isEmpty() {
		return resolved.isEmpty();
	}

	public void add(T entry) {
		Identifier id = registry.getKey(entry);
		if (id == null) return;
		List<String> next = new ArrayList<>(value);
		if (!next.contains(id.toString())) next.add(id.toString());
		set(next);
	}

	public void remove(T entry) {
		Identifier id = registry.getKey(entry);
		if (id == null) return;
		List<String> next = new ArrayList<>(value);
		next.remove(id.toString());
		set(next);
	}

	public void toggle(T entry) {
		if (contains(entry)) remove(entry);
		else add(entry);
	}

	/** All identifiers in the registry, for the GUI picker and command suggestions. */
	public List<Identifier> allIds() {
		List<Identifier> ids = new ArrayList<>(registry.keySet());
		ids.sort((a, b) -> a.toString().compareTo(b.toString()));
		return ids;
	}

	public T resolve(String id) {
		Identifier identifier = Identifier.tryParse(id.trim().toLowerCase(Locale.ROOT));
		if (identifier == null || !registry.containsKey(identifier)) return null;
		return registry.getValue(identifier);
	}

	@Override
	public boolean set(List<String> newValue) {
		boolean accepted = super.set(newValue);
		rebuild();
		return accepted;
	}

	private void rebuild() {
		Set<T> set = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (String id : value) {
			T entry = resolve(id);
			if (entry != null) set.add(entry);
		}
		resolved = Collections.unmodifiableSet(set);
	}

	@Override
	protected List<String> validate(List<String> candidate) {
		LinkedHashSet<String> cleaned = new LinkedHashSet<>();
		for (String id : candidate) {
			if (id == null) continue;
			Identifier identifier = Identifier.tryParse(id.trim().toLowerCase(Locale.ROOT));
			if (identifier != null && registry.containsKey(identifier)) cleaned.add(identifier.toString());
		}
		return List.copyOf(cleaned);
	}

	@Override
	public JsonElement toJson() {
		JsonArray array = new JsonArray();
		for (String id : value) array.add(id);
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

	/** "+id" adds, "-id" removes, "clear" empties, otherwise a comma separated replacement list. */
	@Override
	public boolean parse(String input) {
		if (input == null || input.isBlank()) return false;
		String trimmed = input.trim();
		if (trimmed.equalsIgnoreCase("clear")) return set(List.of());
		if (trimmed.startsWith("+") || trimmed.startsWith("-")) {
			T entry = resolve(trimmed.substring(1));
			if (entry == null) return false;
			if (trimmed.charAt(0) == '+') add(entry);
			else remove(entry);
			return true;
		}
		List<String> list = new ArrayList<>();
		for (String part : trimmed.split(",")) {
			if (resolve(part) == null) return false;
			list.add(part.trim());
		}
		return set(list);
	}

	@Override
	public String getDisplayValue() {
		return resolved.size() + (resolved.size() == 1 ? " entry" : " entries");
	}

	@Override
	public List<String> getSuggestions() {
		return List.of("+minecraft:diamond_ore", "-minecraft:stone", "clear");
	}
}
