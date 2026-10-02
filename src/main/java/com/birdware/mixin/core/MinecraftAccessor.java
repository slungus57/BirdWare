package com.birdware.mixin.core;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Accessor("rightClickDelay")
	int birdware$getRightClickDelay();

	@Accessor("rightClickDelay")
	void birdware$setRightClickDelay(int delay);

	/** Performs a vanilla left click (attack / start breaking) exactly as the attack key would. */
	@Invoker("startAttack")
	boolean birdware$startAttack();

	/** Performs a vanilla right click (use item / place / interact) exactly as the use key would. */
	@Invoker("startUseItem")
	void birdware$startUseItem();
}
