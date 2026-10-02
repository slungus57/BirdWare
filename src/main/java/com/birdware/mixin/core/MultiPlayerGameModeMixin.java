package com.birdware.mixin.core;

import com.birdware.BirdWare;
import com.birdware.event.events.AttackEntityEvent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	private void birdware$preAttack(Player player, Entity target, CallbackInfo ci) {
		if (BirdWare.get().events().post(new AttackEntityEvent(target)).isCancelled()) ci.cancel();
	}

	@Inject(method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V", at = @At("RETURN"))
	private void birdware$postAttack(Player player, Entity target, CallbackInfo ci) {
		BirdWare.get().events().post(new AttackEntityEvent.Post(target));
	}
}
