package com.birdware.gui.theme;

import com.birdware.BirdWare;
import com.birdware.config.JsonFiles;
import com.birdware.util.Animation;
import com.google.gson.JsonElement;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Holds the active {@link Theme}, the built-in presets and user themes saved in {@code birdware/themes/*.json}.
 * The Theme module pushes its settings here via {@link #set(Theme)}.
 */
public final class ThemeManager {
	private static volatile Theme current = Theme.BIRDWARE;

	private final Path dir;
	private final Map<String, Theme> presets = new LinkedHashMap<>();

	public ThemeManager(Path root) {
		this.dir = root.resolve("themes");
		presets.put("BirdWare", Theme.BIRDWARE);
		presets.put("Midnight", new Theme(
			0xFF8B5CF6, 0xFFEC4899, 0xFFF5F3FF, 0xFFA1A1C2, 0x99050510, 0xF00F0F1A, 0xFF17172A, 0x2EFFFFFF, 0x14FFFFFF,
			0x338B5CF6, 0xFFB3B3CC, 0xFF34D399, 0xFFFBBF24, 0xFFF87171, 0.95f, 8f, 0.7f, 1f, true));
		presets.put("Ember", new Theme(
			0xFFFF7A45, 0xFFFFC53D, 0xFFFFF7F0, 0xFFB8A69A, 0x8C0C0604, 0xF01A1210, 0xFF241814, 0x30FFFFFF, 0x14FFFFFF,
			0x33FF7A45, 0xFFC2B2A6, 0xFF73D13D, 0xFFFFC53D, 0xFFFF4D4F, 0.94f, 6f, 0.6f, 1f, true));
		presets.put("Mint", new Theme(
			0xFF34D399, 0xFF22D3EE, 0xFFF0FDF8, 0xFF94B3A8, 0x8C030A08, 0xF0101815, 0xFF15211D, 0x30FFFFFF, 0x14FFFFFF,
			0x3334D399, 0xFFAEC4BC, 0xFF34D399, 0xFFFACC15, 0xFFF87171, 0.94f, 6f, 0.6f, 1f, true));
		presets.put("Sakura", new Theme(
			0xFFF472B6, 0xFFC084FC, 0xFFFFF1F7, 0xFFC4A3B4, 0x8C0D0509, 0xF01C1218, 0xFF261821, 0x30FFFFFF, 0x14FFFFFF,
			0x33F472B6, 0xFFCDB4C2, 0xFF4ADE80, 0xFFFACC15, 0xFFFB7185, 0.94f, 10f, 0.6f, 1f, true));
		presets.put("Mono", new Theme(
			0xFFE5E7EB, 0xFF9CA3AF, 0xFFF9FAFB, 0xFF9CA3AF, 0x8C000000, 0xF0111111, 0xFF1A1A1A, 0x33FFFFFF, 0x14FFFFFF,
			0x26FFFFFF, 0xFFA3A3A3, 0xFF86EFAC, 0xFFFDE68A, 0xFFFCA5A5, 0.95f, 3f, 0.5f, 1f, false));
		presets.put("Light", new Theme(
			0xFF2563EB, 0xFF7C3AED, 0xFF111827, 0xFF6B7280, 0x66E5E7EB, 0xF5FFFFFF, 0xFFF3F4F6, 0x26000000, 0x0F000000,
			0x262563EB, 0xFF6B7280, 0xFF16A34A, 0xFFCA8A04, 0xFFDC2626, 0.97f, 6f, 0.35f, 1f, true));
	}

	/** The active theme. Cheap; call every frame. */
	public static Theme get() {
		return current;
	}

	public void set(Theme theme) {
		current = theme == null ? Theme.BIRDWARE : theme;
		Animation.speedMultiplier = current.animationSpeed();
	}

	public Map<String, Theme> getPresets() {
		return presets;
	}

	public Theme getPreset(String name) {
		for (Map.Entry<String, Theme> entry : presets.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
		}
		return null;
	}

	private static boolean validName(String name) {
		return name != null && name.matches("[A-Za-z0-9_\\-]{1,32}");
	}

	/** Saves a theme as a user theme file. */
	public boolean saveCustom(String name, Theme theme) {
		if (!validName(name)) return false;
		JsonFiles.writeAsync(dir.resolve(name.toLowerCase(Locale.ROOT) + ".json"), theme.toJson());
		return true;
	}

	/** Loads a user theme (or a preset when no file exists). Returns null if unknown/corrupted. */
	public Theme loadCustom(String name) {
		if (!validName(name)) return null;
		Path path = dir.resolve(name.toLowerCase(Locale.ROOT) + ".json");
		JsonElement element = JsonFiles.read(path);
		if (element != null && element.isJsonObject()) return Theme.fromJson(element.getAsJsonObject(), Theme.BIRDWARE);
		return getPreset(name);
	}

	public boolean deleteCustom(String name) {
		if (!validName(name)) return false;
		try {
			return Files.deleteIfExists(dir.resolve(name.toLowerCase(Locale.ROOT) + ".json"));
		} catch (IOException e) {
			BirdWare.LOGGER.error("Failed to delete theme {}", name, e);
			return false;
		}
	}

	public List<String> listCustom() {
		List<String> names = new ArrayList<>();
		if (!Files.isDirectory(dir)) return names;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
			for (Path path : stream) {
				String file = path.getFileName().toString();
				names.add(file.substring(0, file.length() - 5));
			}
		} catch (IOException e) {
			BirdWare.LOGGER.error("Failed to list themes", e);
		}
		names.sort(String::compareToIgnoreCase);
		return names;
	}
}
