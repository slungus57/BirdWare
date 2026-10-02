package com.birdware.mixin.core;

import com.birdware.BirdWare;
import com.birdware.core.PacketManager;
import com.birdware.event.events.PacketEvent;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Packet hooks for the client's own connection only. In singleplayer the integrated server's connections live in the
 * same JVM, so every hook checks {@link #getReceiving()} == CLIENTBOUND.
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
	@Shadow
	public abstract PacketFlow getReceiving();

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
	private void birdware$onSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
		if (getReceiving() != PacketFlow.CLIENTBOUND || PacketManager.isBypassing() || BirdWare.get() == null) return;
		if (BirdWare.get().events().post(new PacketEvent.Send(packet)).isCancelled()) ci.cancel();
	}

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("RETURN"))
	private void birdware$afterSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
		if (getReceiving() != PacketFlow.CLIENTBOUND || PacketManager.isBypassing() || BirdWare.get() == null) return;
		if (BirdWare.get().events().hasListeners(PacketEvent.Sent.class)) {
			BirdWare.get().events().post(new PacketEvent.Sent(packet));
		}
	}

	@Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
	private void birdware$onReceive(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
		if (getReceiving() != PacketFlow.CLIENTBOUND || BirdWare.get() == null) return;
		if (BirdWare.get().events().post(new PacketEvent.Receive(packet)).isCancelled()) ci.cancel();
	}
}
