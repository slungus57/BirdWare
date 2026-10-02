package com.birdware.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;

/**
 * An enum-backed choice. The GUI shows {@link Object#toString()} of each constant, so enums should override it with a
 * friendly name (or implement {@link Nameable}).
 */
public class EnumSetting<E extends Enum<E>> extends Setting<E> {
	private final E[] constants;

	public EnumSetting(String name, String description, E defaultValue) {
		super(name, description, defaultValue);
		this.constants = defaultValue.getDeclaringClass().getEnumConstants();
	}

	/** Optional friendly naming for enum constants. */
	public interface Nameable {
		String displayName();
	}

	public E[] getConstants() {
		return constants.clone();
	}

	public boolean is(E constant) {
		return value == constant;
	}

	public void cycle(boolean forward) {
		int n = constants.length;
		int i = value.ordinal();
		set(constants[((forward ? i + 1 : i - 1) % n + n) % n]);
	}

	public static String nameOf(Enum<?> constant) {
		if (constant instanceof Nameable nameable) return nameable.displayName();
		return constant.toString();
	}

	@Override
	public String getDisplayValue() {
		return nameOf(value);
	}

	private E lookup(String input) {
		if (input == null) return null;
		String trimmed = input.trim();
		for (E constant : constants) {
			if (constant.name().equalsIgnoreCase(trimmed) || nameOf(constant).equalsIgnoreCase(trimmed)
				|| Setting.idOf(nameOf(constant)).equals(Setting.idOf(trimmed))) {
				return constant;
			}
		}
		return null;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value.name());
	}

	@Override
	public boolean fromJson(JsonElement element) {
		if (element == null || !element.isJsonPrimitive()) return false;
		E constant = lookup(element.getAsString());
		return constant != null && set(constant);
	}

	@Override
	public boolean parse(String input) {
		E constant = lookup(input);
		return constant != null && set(constant);
	}

	@Override
	public List<String> getSuggestions() {
		List<String> list = new ArrayList<>(constants.length);
		for (E constant : constants) list.add(Setting.idOf(nameOf(constant)));
		return list;
	}
}
