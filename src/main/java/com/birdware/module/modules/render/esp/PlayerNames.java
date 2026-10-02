package com.birdware.module.modules.render.esp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded UUID to player-name cache. Names are resolved from loaded player entities first and then from the tab list
 * ({@link PlayerInfo}), which also covers players outside render distance. Misses are remembered and retried only
 * every {@link #RETRY_MS} so an offline owner does not trigger a lookup every frame. Client thread only.
 */
public final class PlayerNames {
	private static final long RETRY_MS = 5000L;
	private static final int MAX_ENTRIES = 512;

	private final Map<UUID, Entry> cache = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<UUID, Entry> eldest) {
			return size() > MAX_ENTRIES;
		}
	};

	private static final class Entry {
		String name;
		long checkedAt;
	}

	/**
	 * @return the player's name, or null when the UUID belongs to nobody currently online (or loaded)
	 */
	public String resolve(UUID uuid) {
		if (uuid == null) return null;
		long now = System.currentTimeMillis();
		Entry entry = cache.get(uuid);
		if (entry != null && (entry.name != null || now - entry.checkedAt < RETRY_MS)) return entry.name;
		String name = lookup(uuid);
		if (entry == null) {
			entry = new Entry();
			cache.put(uuid, entry);
		}
		entry.name = name;
		entry.checkedAt = now;
		return name;
	}

	private static String lookup(UUID uuid) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			Player player = mc.level.getPlayerByUUID(uuid);
			if (player != null) return player.getGameProfile().name();
		}
		ClientPacketListener connection = mc.getConnection();
		if (connection != null) {
			PlayerInfo info = connection.getPlayerInfo(uuid);
			if (info != null) return info.getProfile().name();
		}
		return null;
	}

	/** First eight hex digits of a UUID, used to tell unknown owners apart. */
	public static String shortId(UUID uuid) {
		String s = uuid.toString();
		return s.substring(0, 8);
	}

	public void clear() {
		cache.clear();
	}
}
