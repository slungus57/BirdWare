package com.birdware.config;

import com.google.gson.JsonElement;

/**
 * Global (profile independent) state persisted in {@code birdware/client.json}: GUI layout, theme, preferences.
 * Register with {@link ConfigManager#registerComponent(ConfigComponent)}.
 */
public interface ConfigComponent {
	/** Unique key in client.json. */
	String configKey();

	JsonElement saveConfig();

	/** Must tolerate null, wrong types and missing fields without throwing. */
	void loadConfig(JsonElement element);
}
