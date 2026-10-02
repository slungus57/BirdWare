package com.birdware.mixin.core;

import com.birdware.BirdWare;
import com.birdware.event.events.KeyInputEvent;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true)
	private void birdware$onKeyPress(long window, int action, KeyEvent keyEvent, CallbackInfo ci) {
		if (window != minecraft.getWindow().handle() || BirdWare.get() == null) return;
		KeyInputEvent event = new KeyInputEvent(keyEvent.key(), keyEvent.scancode(), action, keyEvent.modifiers(), minecraft.screen != null);
		if (BirdWare.get().events().post(event).isCancelled()) ci.cancel();
	}
}
