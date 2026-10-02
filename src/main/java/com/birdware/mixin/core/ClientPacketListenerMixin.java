package com.birdware.mixin.core;

import com.birdware.BirdWare;
import com.birdware.event.events.PacketEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Packets inside a {@code ClientboundBundlePacket} bypass {@code Connection#channelRead0}; this posts
 * {@link PacketEvent.Receive} for each of them (on the client thread) so filters like Velocity see them too.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@WrapOperation(method = "handleBundlePacket(Lnet/minecraft/network/protocol/game/ClientboundBundlePacket;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"))
	private void birdware$onBundledPacket(Packet<?> packet, PacketListener listener, Operation<Void> original) {
		if (BirdWare.get() != null && BirdWare.get().events().post(new PacketEvent.Receive(packet)).isCancelled()) return;
		original.call(packet, listener);
	}
}
