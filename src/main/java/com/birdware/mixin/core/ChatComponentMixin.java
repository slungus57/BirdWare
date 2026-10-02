package com.birdware.mixin.core;

import com.birdware.BirdWare;
import com.birdware.event.events.ChatReceiveEvent;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@Unique
	private boolean birdware$reentrant;

	@Shadow
	public abstract void addMessage(Component component, MessageSignature signature, GuiMessageTag tag);

	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
		at = @At("HEAD"), cancellable = true)
	private void birdware$onAddMessage(Component component, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
		if (birdware$reentrant || BirdWare.get() == null) return;
		ChatReceiveEvent event = BirdWare.get().events().post(new ChatReceiveEvent(component));
		if (event.isCancelled()) {
			ci.cancel();
			return;
		}
		if (event.getMessage() != component && event.getMessage() != null) {
			ci.cancel();
			birdware$reentrant = true;
			try {
				addMessage(event.getMessage(), signature, tag);
			} finally {
				birdware$reentrant = false;
			}
		}
	}
}
