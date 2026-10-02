package com.birdware.mixin.core;

import com.birdware.BirdWare;
import com.birdware.event.events.ScreenOpenEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"), cancellable = true)
	private void birdware$onSetScreen(Screen screen, CallbackInfo ci) {
		if (BirdWare.get() == null) return;
		if (BirdWare.get().events().post(new ScreenOpenEvent(screen)).isCancelled()) ci.cancel();
	}
}
