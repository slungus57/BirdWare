package com.birdware.config;

import com.birdware.BirdWare;
import com.birdware.module.Module;
import com.birdware.setting.ButtonSetting;
import com.birdware.setting.Setting;
import com.birdware.setting.SettingGroup;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Persists module state into named profiles and global state into client.json.
 *
 * <pre>
 * .minecraft/birdware/
 *   client.json            active profile + {@link ConfigComponent}s (GUI, theme, preferences)
 *   profiles/&lt;name&gt;.json  module enabled state, settings, binds and module data
 *   friends.json, waypoints.json, ... (owned by their managers via {@link JsonFiles})
 * </pre>
 *
 * Loading is fully defensive: unknown modules/settings are ignored, invalid values keep defaults, and settings that
 * moved to another group are still found by id.
 */
public final class ConfigManager {
	public static final int FORMAT_VERSION = 1;
	public static final String DEFAULT_PROFILE = "default";
	private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,32}");

	private final Path root;
	private final Path profilesDir;
	private final Path clientFile;
	private final Map<String, ConfigComponent> components = new LinkedHashMap<>();
	private String activeProfile = DEFAULT_PROFILE;
	private String lastSavedProfileJson;
	private String lastSavedClientJson;

	public ConfigManager(Path root) {
		this.root = root;
		this.profilesDir = root.resolve("profiles");
		this.clientFile = root.resolve("client.json");
		try {
			Files.createDirectories(profilesDir);
		} catch (IOException e) {
			BirdWare.LOGGER.error("Could not create config directories in {}", root, e);
		}
	}

	public Path getRoot() {
		return root;
	}

	public void registerComponent(ConfigComponent component) {
		components.put(component.configKey(), component);
	}

	public String getActiveProfile() {
		return activeProfile;
	}

	// ------------------------------------------------------------------------------------------------ startup / shutdown

	/** Loads client.json and the active profile. Called once after all modules are registered. */
	public void loadAll() {
		JsonElement client = JsonFiles.read(clientFile);
		if (client != null && client.isJsonObject()) {
			JsonObject object = client.getAsJsonObject();
			String profile = getString(object, "activeProfile");
			if (profile != null && isValidName(profile)) activeProfile = profile;
			loadComponents(object.has("components") && object.get("components").isJsonObject() ? object.getAsJsonObject("components") : new JsonObject());
		} else {
			loadComponents(new JsonObject());
		}
		if (!profileExists(activeProfile)) {
			activeProfile = DEFAULT_PROFILE;
			if (!profileExists(DEFAULT_PROFILE)) {
				// First launch: apply defaults (enables DefaultEnabled modules) and persist them.
				applyModules(null);
				saveProfile(DEFAULT_PROFILE, false);
				saveClient(false);
				return;
			}
		}
		loadProfile(activeProfile);
	}

	private void loadComponents(JsonObject object) {
		for (ConfigComponent component : components.values()) {
			try {
				component.loadConfig(object.get(component.configKey()));
			} catch (Throwable t) {
				BirdWare.LOGGER.error("Failed to load config component {}", component.configKey(), t);
			}
		}
	}

	/** Saves client.json and the active profile; skips files whose content is unchanged. */
	public void saveAll(boolean async) {
		saveClient(async);
		saveProfile(activeProfile, async);
	}

	public void saveClient(boolean async) {
		JsonObject object = new JsonObject();
		object.addProperty("version", FORMAT_VERSION);
		object.addProperty("activeProfile", activeProfile);
		JsonObject comps = new JsonObject();
		for (ConfigComponent component : components.values()) {
			try {
				JsonElement element = component.saveConfig();
				if (element != null) comps.add(component.configKey(), element);
			} catch (Throwable t) {
				BirdWare.LOGGER.error("Failed to save config component {}", component.configKey(), t);
			}
		}
		object.add("components", comps);
		String json = JsonFiles.GSON.toJson(object);
		if (json.equals(lastSavedClientJson)) return;
		lastSavedClientJson = json;
		if (async) JsonFiles.writeAsync(clientFile, object);
		else JsonFiles.write(clientFile, object);
	}

	// ------------------------------------------------------------------------------------------------ profiles

	public static boolean isValidName(String name) {
		return name != null && VALID_NAME.matcher(name).matches();
	}

	private Path profilePath(String name) {
		return profilesDir.resolve(name.toLowerCase(Locale.ROOT) + ".json");
	}

	public boolean profileExists(String name) {
		return isValidName(name) && Files.isRegularFile(profilePath(name));
	}

	public List<String> listProfiles() {
		List<String> names = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(profilesDir, "*.json")) {
			for (Path path : stream) {
				String file = path.getFileName().toString();
				String name = file.substring(0, file.length() - 5);
				if (isValidName(name)) names.add(name);
			}
		} catch (IOException e) {
			BirdWare.LOGGER.error("Could not list profiles", e);
		}
		names.sort(String::compareToIgnoreCase);
		return names;
	}

	/** Serialises all modules into a profile object. */
	public JsonObject serializeModules() {
		JsonObject root = new JsonObject();
		root.addProperty("version", FORMAT_VERSION);
		JsonObject modules = new JsonObject();
		for (Module module : BirdWare.get().modules().getModules()) {
			JsonObject m = new JsonObject();
			m.addProperty("enabled", module.isEnabled());
			JsonObject groups = new JsonObject();
			for (SettingGroup group : module.getGroups()) {
				JsonObject g = new JsonObject();
				for (Setting<?> setting : group.getSettings()) {
					if (setting instanceof ButtonSetting) continue;
					try {
						g.add(setting.getId(), setting.toJson());
					} catch (Throwable t) {
						BirdWare.LOGGER.error("Failed to serialise {}.{}", module.getName(), setting.getName(), t);
					}
				}
				groups.add(group.getId(), g);
			}
			m.add("settings", groups);
			JsonObject data = new JsonObject();
			try {
				module.saveData(data);
			} catch (Throwable t) {
				BirdWare.LOGGER.error("Failed to save data of {}", module.getName(), t);
			}
			if (!data.isEmpty()) m.add("data", data);
			modules.add(module.getId(), m);
		}
		root.add("modules", modules);
		return root;
	}

	/** Applies a profile object: every module is first reset to defaults/disabled, then the file's values applied. */
	public void applyModules(JsonObject root) {
		JsonObject modules = root != null && root.has("modules") && root.get("modules").isJsonObject()
			? root.getAsJsonObject("modules") : new JsonObject();
		for (Module module : BirdWare.get().modules().getModules()) {
			JsonElement element = modules.get(module.getId());
			JsonObject m = element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
			try {
				for (SettingGroup group : module.getGroups()) {
					for (Setting<?> setting : group.getSettings()) setting.reset();
				}
				if (m != null) {
					JsonObject groups = m.has("settings") && m.get("settings").isJsonObject() ? m.getAsJsonObject("settings") : new JsonObject();
					for (SettingGroup group : module.getGroups()) {
						JsonObject g = groups.has(group.getId()) && groups.get(group.getId()).isJsonObject() ? groups.getAsJsonObject(group.getId()) : null;
						for (Setting<?> setting : group.getSettings()) {
							JsonElement value = g != null ? g.get(setting.getId()) : null;
							if (value == null) value = findAnywhere(groups, setting.getId());
							if (value != null && !setting.fromJson(value)) {
								BirdWare.LOGGER.warn("Invalid value for {}.{}: {} (keeping default)", module.getName(), setting.getName(), value);
							}
						}
					}
					JsonElement data = m.get("data");
					module.loadData(data != null && data.isJsonObject() ? data.getAsJsonObject() : new JsonObject());
				} else {
					module.loadData(new JsonObject());
				}
				boolean enabled = m != null ? getBoolean(m, "enabled", false) : defaultEnabled(module);
				module.setEnabledSilently(enabled);
			} catch (Throwable t) {
				BirdWare.LOGGER.error("Failed to apply profile data to {}", module.getName(), t);
			}
		}
	}

	private static boolean defaultEnabled(Module module) {
		return module instanceof DefaultEnabled;
	}

	private static JsonElement findAnywhere(JsonObject groups, String settingId) {
		for (Map.Entry<String, JsonElement> entry : groups.entrySet()) {
			if (entry.getValue().isJsonObject()) {
				JsonElement value = entry.getValue().getAsJsonObject().get(settingId);
				if (value != null) return value;
			}
		}
		return null;
	}

	/** Loads the named profile and makes it active. Returns false if it does not exist or is invalid. */
	public boolean loadProfile(String name) {
		if (!isValidName(name)) return false;
		Path path = profilePath(name);
		JsonElement element = Files.isRegularFile(path) ? JsonFiles.read(path) : null;
		if (element == null && Files.isRegularFile(path)) return false;
		if (element != null && !element.isJsonObject()) return false;
		activeProfile = name.toLowerCase(Locale.ROOT);
		applyModules(element != null ? element.getAsJsonObject() : null);
		lastSavedProfileJson = null;
		saveClient(true);
		return true;
	}

	/** Writes the current module state to the named profile. */
	public boolean saveProfile(String name, boolean async) {
		if (!isValidName(name)) return false;
		JsonObject object = serializeModules();
		String json = JsonFiles.GSON.toJson(object);
		boolean isActive = name.equalsIgnoreCase(activeProfile);
		if (isActive && json.equals(lastSavedProfileJson) && profileExists(name)) return true;
		if (isActive) lastSavedProfileJson = json;
		if (async) {
			JsonFiles.writeAsync(profilePath(name), object);
			return true;
		}
		return JsonFiles.write(profilePath(name), object);
	}

	/** Creates a new profile from the current state (copy) and switches to it. */
	public boolean createProfile(String name) {
		if (!isValidName(name) || profileExists(name)) return false;
		saveProfile(activeProfile, false);
		if (!saveProfile(name, false)) return false;
		activeProfile = name.toLowerCase(Locale.ROOT);
		lastSavedProfileJson = null;
		saveClient(true);
		return true;
	}

	public boolean renameProfile(String from, String to) {
		if (!profileExists(from) || !isValidName(to) || profileExists(to)) return false;
		try {
			Files.move(profilePath(from), profilePath(to));
		} catch (IOException e) {
			BirdWare.LOGGER.error("Failed to rename profile {} to {}", from, to, e);
			return false;
		}
		if (activeProfile.equalsIgnoreCase(from)) {
			activeProfile = to.toLowerCase(Locale.ROOT);
			saveClient(true);
		}
		return true;
	}

	/** Deletes a profile. Deleting the active profile switches to "default" (recreated if needed). */
	public boolean deleteProfile(String name) {
		if (!profileExists(name)) return false;
		try {
			Files.delete(profilePath(name));
		} catch (IOException e) {
			BirdWare.LOGGER.error("Failed to delete profile {}", name, e);
			return false;
		}
		if (activeProfile.equalsIgnoreCase(name)) {
			if (!profileExists(DEFAULT_PROFILE)) {
				activeProfile = DEFAULT_PROFILE;
				lastSavedProfileJson = null;
				saveProfile(DEFAULT_PROFILE, false);
			}
			loadProfile(DEFAULT_PROFILE);
		}
		return true;
	}

	// ------------------------------------------------------------------------------------------------ helpers

	public static String getString(JsonObject object, String key) {
		try {
			JsonElement e = object.get(key);
			return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
		} catch (RuntimeException ex) {
			return null;
		}
	}

	public static boolean getBoolean(JsonObject object, String key, boolean fallback) {
		try {
			JsonElement e = object.get(key);
			return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : fallback;
		} catch (RuntimeException ex) {
			return fallback;
		}
	}

	public static double getDouble(JsonObject object, String key, double fallback) {
		try {
			JsonElement e = object.get(key);
			if (e == null || !e.isJsonPrimitive()) return fallback;
			double v = e.getAsDouble();
			return Double.isFinite(v) ? v : fallback;
		} catch (RuntimeException ex) {
			return fallback;
		}
	}

	public static int getInt(JsonObject object, String key, int fallback) {
		try {
			JsonElement e = object.get(key);
			return e != null && e.isJsonPrimitive() ? e.getAsInt() : fallback;
		} catch (RuntimeException ex) {
			return fallback;
		}
	}

	/** Marker for modules that are enabled on a fresh profile (HUD watermark, ArrayList, notifications...). */
	public interface DefaultEnabled {
	}
}
