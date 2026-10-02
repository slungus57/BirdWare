package com.birdware.module.modules.render.esp;

import com.birdware.BirdWare;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Short-lived friend lookup cache for render code. {@link com.birdware.friend.FriendManager#isFriend(Entity)}
 * normalises the name with a regex on every call, which is far too expensive to run for every player in every frame
 * of several ESP modules. Results are cached by entity id and the cache is dropped every {@link #TTL_NS} so friend list
 * changes show up almost immediately. Render/client thread only.
 */
public final class FriendCache {
	private static final long TTL_NS = 250_000_000L;
	private static final int MAX_ENTRIES = 2048;
	private static final byte UNKNOWN = 0;
	private static final byte FRIEND = 1;
	private static final byte STRANGER = 2;

	private static final Int2ByteOpenHashMap CACHE = new Int2ByteOpenHashMap();
	private static long stamp = System.nanoTime();

	static {
		CACHE.defaultReturnValue(UNKNOWN);
	}

	private FriendCache() {
	}

	/** True if the entity is a player on the BirdWare friend list. */
	public static boolean isFriend(Entity entity) {
		if (!(entity instanceof Player player)) return false;
		long now = System.nanoTime();
		if (now - stamp > TTL_NS || CACHE.size() > MAX_ENTRIES) {
			CACHE.clear();
			stamp = now;
		}
		byte cached = CACHE.get(player.getId());
		if (cached != UNKNOWN) return cached == FRIEND;
		BirdWare birdWare = BirdWare.get();
		boolean friend = birdWare != null && birdWare.friends().isFriend(player);
		CACHE.put(player.getId(), friend ? FRIEND : STRANGER);
		return friend;
	}

	/** Drops every cached entry (world change). */
	public static void clear() {
		CACHE.clear();
		stamp = System.nanoTime();
	}
}
