package com.birdware.setting;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A single configurable value. Settings are declared as fields on modules (or other configurable objects), grouped in
 * {@link SettingGroup}s, and automatically picked up by the ClickGUI, the config system and the {@code .bw set}
 * command.
 *
 * <p>Implementations must make {@link #fromJson(JsonElement)} and {@link #parse(String)} lenient: invalid input is
 * rejected (returns false / keeps the current value) and never throws.
 *
 * @param <T> value type. Values should be immutable (records, boxed primitives, immutable collections).
 */
public abstract class Setting<T> {
	private final String name;
	private final String description;
	protected final T defaultValue;
	protected T value;
	private BooleanSupplier visibility = () -> true;
	private final List<Consumer<T>> changeListeners = new ArrayList<>(1);
	private SettingGroup group;

	protected Setting(String name, String description, T defaultValue) {
		this.name = Objects.requireNonNull(name);
		this.description = description == null ? "" : description;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public T get() {
		return value;
	}

	public T getDefault() {
		return defaultValue;
	}

	/**
	 * Validates and stores a new value. Listeners are notified only when the stored value actually changes.
	 * @return true when the value was accepted (even if unchanged).
	 */
	public boolean set(T newValue) {
		if (newValue == null) return false;
		T validated = validate(newValue);
		if (validated == null) return false;
		if (!Objects.equals(value, validated)) {
			value = validated;
			for (Consumer<T> listener : changeListeners) {
				try {
					listener.accept(validated);
				} catch (RuntimeException e) {
					com.birdware.BirdWare.LOGGER.error("Setting listener for {} failed", name, e);
				}
			}
		}
		return true;
	}

	/** Returns a corrected value, or null to reject. Default accepts everything. */
	protected T validate(T candidate) {
		return candidate;
	}

	public void reset() {
		set(defaultValue);
	}

	public boolean isDefault() {
		return Objects.equals(value, defaultValue);
	}

	public boolean isVisible() {
		return visibility.getAsBoolean() && (group == null || group.isVisible());
	}

	/** Only show (and let commands target) this setting while the supplier returns true. */
	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S visibleWhen(BooleanSupplier supplier) {
		this.visibility = Objects.requireNonNull(supplier);
		return (S) this;
	}

	/** Registers a change listener; it is called on the thread that changed the value (normally the client thread). */
	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S onChange(Consumer<T> listener) {
		changeListeners.add(listener);
		return (S) this;
	}

	public List<Consumer<T>> getChangeListeners() {
		return Collections.unmodifiableList(changeListeners);
	}

	public SettingGroup getGroup() {
		return group;
	}

	void setGroup(SettingGroup group) {
		this.group = group;
	}

	/** Serializes the current value. */
	public abstract JsonElement toJson();

	/** Loads a value; must not throw. Returns false when the element was unusable (value left unchanged). */
	public abstract boolean fromJson(JsonElement element);

	/** Parses user input (commands, text fields). Must not throw. */
	public abstract boolean parse(String input);

	/** Human readable value used by the GUI, commands and tooltips. */
	public String getDisplayValue() {
		return String.valueOf(value);
	}

	/** Suggestions for command completion / help output. */
	public List<String> getSuggestions() {
		return List.of();
	}

	/** Machine friendly id: lowercase name without spaces, used by commands and config keys. */
	public String getId() {
		return idOf(name);
	}

	public static String idOf(String name) {
		StringBuilder sb = new StringBuilder(name.length());
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (Character.isLetterOrDigit(c)) sb.append(Character.toLowerCase(c));
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		return name + "=" + getDisplayValue();
	}
}
