package com.birdware.mixin.core;

import com.birdware.BirdWare;
import com.birdware.event.events.MotionEvent;
import com.birdware.event.events.PlayerMoveEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Unique
	private float birdware$realYaw;
	@Unique
	private float birdware$realPitch;
	@Unique
	private boolean birdware$realOnGround;
	@Unique
	private boolean birdware$spoofing;

	@Inject(method = "sendPosition()V", at = @At("HEAD"))
	private void birdware$preSendPosition(CallbackInfo ci) {
		if (BirdWare.get() == null) return;
		LocalPlayer self = (LocalPlayer) (Object) this;
		birdware$realYaw = self.getYRot();
		birdware$realPitch = self.getXRot();
		birdware$realOnGround = self.onGround();
		MotionEvent.Pre event = BirdWare.get().events().post(MotionEvent.Pre.INSTANCE.reset(
			self.getX(), self.getY(), self.getZ(), birdware$realYaw, birdware$realPitch, birdware$realOnGround));
		birdware$spoofing = true;
		if (event.getYaw() != birdware$realYaw) self.setYRot(event.getYaw());
		if (event.getPitch() != birdware$realPitch) self.setXRot(event.getPitch());
		if (event.isOnGround() != birdware$realOnGround) ((EntityAccessor) self).birdware$setOnGroundRaw(event.isOnGround());
	}

	@Inject(method = "sendPosition()V", at = @At("RETURN"))
	private void birdware$postSendPosition(CallbackInfo ci) {
		if (!birdware$spoofing) return;
		birdware$spoofing = false;
		LocalPlayer self = (LocalPlayer) (Object) this;
		self.setYRot(birdware$realYaw);
		self.setXRot(birdware$realPitch);
		if (self.onGround() != birdware$realOnGround) ((EntityAccessor) self).birdware$setOnGroundRaw(birdware$realOnGround);
		BirdWare.get().events().post(MotionEvent.Post.INSTANCE);
	}

	@ModifyVariable(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), argsOnly = true)
	private Vec3 birdware$onMove(Vec3 movement, MoverType type) {
		if (BirdWare.get() == null) return movement;
		PlayerMoveEvent event = BirdWare.get().events().post(PlayerMoveEvent.INSTANCE.reset(type, movement));
		return event.getMovement() != null ? event.getMovement() : movement;
	}
}
