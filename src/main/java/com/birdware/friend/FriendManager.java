package com.birdware.friend;

import com.birdware.config.JsonFiles;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Friend list (by player name, case insensitive), persisted in {@code birdware/friends.json}. Combat modules skip
 * friends when the Friends module's protection is on; ESP, Tracers, Nametags and Chams draw friends in the friend
 * color. Thread safe: lookups happen from render, tick and network threads.
 */
public final class FriendManager {
	private final Path file;
	private final Set<String> friends = ConcurrentHashMap.newKeySet();

	public FriendManager(Path root) {
		this.file = root.resolve("friends.json");
	}

	public void load() {
		friends.clear();
		JsonElement element = JsonFiles.read(file);
		if (element == null || !element.isJsonArray()) return;
		for (JsonElement e : element.getAsJsonArray()) {
			if (e.isJsonPrimitive()) {
				String name = normalize(e.getAsString());
				if (name != null) friends.add(name);
			}
		}
	}

	public void save() {
		JsonArray array = new JsonArray();
		for (String name : list()) array.add(name);
		JsonFiles.writeAsync(file, array);
	}

	private static String normalize(String name) {
		if (name == null) return null;
		String trimmed = name.trim();
		if (trimmed.isEmpty() || trimmed.length() > 16 || !trimmed.matches("[A-Za-z0-9_]+")) return null;
		return trimmed.toLowerCase(Locale.ROOT);
	}

	/** @return false if the name is invalid or already a friend */
	public boolean add(String name) {
		String n = normalize(name);
		if (n == null || !friends.add(n)) return false;
		save();
		return true;
	}

	public boolean remove(String name) {
		String n = normalize(name);
		if (n == null || !friends.remove(n)) return false;
		save();
		return true;
	}

	/** Adds or removes; returns the new friend state. */
	public boolean toggle(String name) {
		if (isFriend(name)) {
			remove(name);
			return false;
		}
		return add(name);
	}

	public void clear() {
		friends.clear();
		save();
	}

	public boolean isFriend(String name) {
		String n = normalize(name);
		return n != null && friends.contains(n);
	}

	public boolean isFriend(Entity entity) {
		return entity instanceof Player player && isFriend(player.getGameProfile().name());
	}

	public int size() {
		return friends.size();
	}

	public List<String> list() {
		List<String> list = new ArrayList<>(friends);
		Collections.sort(list);
		return list;
	}
}
