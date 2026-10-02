package com.birdware.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;

/**
 * Sending helpers for modules.
 * <ul>
 *     <li>{@link #send(Packet)} goes through the normal pipeline (other modules see it as a
 *     {@link com.birdware.event.events.PacketEvent.Send}).</li>
 *     <li>{@link #sendDirect(Packet)} bypasses BirdWare's send hook entirely. Used to release buffered packets
 *     (Blink, PingSpoof) so they are not captured again.</li>
 * </ul>
 */
public final class PacketManager {
	private static final ThreadLocal<int[]> BYPASS = ThreadLocal.withInitial(() -> new int[1]);

	/** True while {@link #sendDirect} is executing on this thread; the Connection mixin skips events then. */
	public static boolean isBypassing() {
		return BYPASS.get()[0] > 0;
	}

	/** Sends through the normal pipeline. No-op when not connected. */
	public void send(Packet<?> packet) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection != null) connection.send(packet);
	}

	/** Sends without posting PacketEvent.Send. No-op when not connected. */
	public void sendDirect(Packet<?> packet) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null) return;
		int[] depth = BYPASS.get();
		depth[0]++;
		try {
			connection.send(packet);
		} finally {
			depth[0]--;
		}
	}
}
