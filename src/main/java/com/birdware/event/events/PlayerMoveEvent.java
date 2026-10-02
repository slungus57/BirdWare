package com.birdware.event.events;

import com.birdware.event.Event;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Posted when the local player is about to move (start of {@code LocalPlayer#move}). The movement vector can be
 * replaced (Speed, Flight, Phase, SafeWalk-style edge logic). Collision is still applied by vanilla afterwards.
 */
public final class PlayerMoveEvent extends Event {
	public static final PlayerMoveEvent INSTANCE = new PlayerMoveEvent();

	private MoverType type;
	private Vec3 movement;

	private PlayerMoveEvent() {
	}

	public PlayerMoveEvent reset(MoverType type, Vec3 movement) {
		this.type = type;
		this.movement = movement;
		return this;
	}

	public MoverType getType() {
		return type;
	}

	public Vec3 getMovement() {
		return movement;
	}

	public void setMovement(Vec3 movement) {
		this.movement = movement;
	}

	public void setX(double x) {
		movement = new Vec3(x, movement.y, movement.z);
	}

	public void setY(double y) {
		movement = new Vec3(movement.x, y, movement.z);
	}

	public void setZ(double z) {
		movement = new Vec3(movement.x, movement.y, z);
	}

	public void setHorizontal(double x, double z) {
		movement = new Vec3(x, movement.y, z);
	}
}
