package com.birdware.event.events;

import com.birdware.event.CancellableEvent;
import net.minecraft.network.protocol.Packet;

/**
 * Packet traffic of the client's server connection.
 * <ul>
 *     <li>{@link Send}: posted on the thread calling {@code Connection#send} (normally the client thread) before the
 *     packet is written. Cancel to drop it (Blink/PingSpoof buffer it and re-send through
 *     {@link com.birdware.core.PacketManager#sendDirect}).</li>
 *     <li>{@link Receive}: posted before the packet is dispatched to its handler. Usually on the <b>netty thread</b>;
 *     packets inside bundle packets are posted on the client thread. Cancel to drop it. Listeners must not touch
 *     world/player state directly here; record data or schedule work with {@code mc.execute(...)}.</li>
 * </ul>
 */
public abstract class PacketEvent extends CancellableEvent {
	private final Packet<?> packet;

	protected PacketEvent(Packet<?> packet) {
		this.packet = packet;
	}

	public Packet<?> getPacket() {
		return packet;
	}

	public static final class Send extends PacketEvent {
		public Send(Packet<?> packet) {
			super(packet);
		}
	}

	public static final class Receive extends PacketEvent {
		public Receive(Packet<?> packet) {
			super(packet);
		}
	}

	/** Posted on the client thread after an outbound packet was handed to netty (not cancellable in effect). */
	public static final class Sent extends PacketEvent {
		public Sent(Packet<?> packet) {
			super(packet);
		}
	}
}
