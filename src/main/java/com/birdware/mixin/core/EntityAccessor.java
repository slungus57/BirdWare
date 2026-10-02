package com.birdware.mixin.core;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAccessor {
	/** Sets the raw onGround flag without {@code checkSupportingBlock} side effects. */
	@Accessor("onGround")
	void birdware$setOnGroundRaw(boolean onGround);
}
