package com.birdware.mixin.core;

import com.birdware.BirdWare;
import com.birdware.event.events.MouseClickEvent;
import com.birdware.event.events.MouseScrollEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"), cancellable = true)
	private void birdware$onButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (window != minecraft.getWindow().handle() || BirdWare.get() == null) return;
		MouseClickEvent event = new MouseClickEvent(info.button(), action, info.modifiers(), minecraft.screen != null);
		if (BirdWare.get().events().post(event).isCancelled()) ci.cancel();
	}

	@Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
	private void birdware$onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (window != minecraft.getWindow().handle() || BirdWare.get() == null) return;
		MouseScrollEvent event = new MouseScrollEvent(horizontal, vertical, minecraft.screen != null);
		if (BirdWare.get().events().post(event).isCancelled()) ci.cancel();
	}
}
