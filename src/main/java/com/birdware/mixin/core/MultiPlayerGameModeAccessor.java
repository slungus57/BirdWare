package com.birdware.mixin.core;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
	/** Sends ServerboundSetCarriedItemPacket immediately if the selected slot changed. */
	@Invoker("ensureHasSentCarriedItem")
	void birdware$syncSelectedSlot();

	@Accessor("destroyDelay")
	int birdware$getDestroyDelay();

	@Accessor("destroyDelay")
	void birdware$setDestroyDelay(int delay);

	@Accessor("destroyProgress")
	float birdware$getDestroyProgress();

	@Accessor("destroyProgress")
	void birdware$setDestroyProgress(float progress);

	@Accessor("carriedIndex")
	int birdware$getCarriedIndex();
}
