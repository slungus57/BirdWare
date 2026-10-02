package com.birdware.setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/** A named, collapsible block of settings. */
public final class SettingGroup {
	private final String name;
	private final List<Setting<?>> settings = new ArrayList<>();
	private boolean expanded;
	private BooleanSupplier visibility = () -> true;

	public SettingGroup(String name, boolean expandedByDefault) {
		this.name = name;
		this.expanded = expandedByDefault;
	}

	public String getName() {
		return name;
	}

	public <S extends Setting<?>> S add(S setting) {
		for (Setting<?> existing : settings) {
			if (existing.getId().equals(setting.getId())) {
				throw new IllegalArgumentException("Duplicate setting '" + setting.getName() + "' in group " + name);
			}
		}
		setting.setGroup(this);
		settings.add(setting);
		return setting;
	}

	public List<Setting<?>> getSettings() {
		return Collections.unmodifiableList(settings);
	}

	public Setting<?> get(String nameOrId) {
		String id = Setting.idOf(nameOrId);
		for (Setting<?> setting : settings) {
			if (setting.getId().equals(id)) return setting;
		}
		return null;
	}

	public boolean isExpanded() {
		return expanded;
	}

	public void setExpanded(boolean expanded) {
		this.expanded = expanded;
	}

	public boolean isVisible() {
		return visibility.getAsBoolean();
	}

	public SettingGroup visibleWhen(BooleanSupplier supplier) {
		this.visibility = supplier;
		return this;
	}

	public String getId() {
		return Setting.idOf(name);
	}

	public void resetAll() {
		for (Setting<?> setting : settings) setting.reset();
	}
}
