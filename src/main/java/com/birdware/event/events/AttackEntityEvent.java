package com.birdware.event.events;

import com.birdware.event.CancellableEvent;
import net.minecraft.world.entity.Entity;

/**
 * Posted before {@code MultiPlayerGameMode#attack} sends an attack (from the player, KillAura, TriggerBot...).
 * Cancel to prevent the attack (e.g. friend protection). {@link Post} follows a successful attack.
 */
public class AttackEntityEvent extends CancellableEvent {
	private final Entity target;

	public AttackEntityEvent(Entity target) {
		this.target = target;
	}

	public Entity getTarget() {
		return target;
	}

	public static final class Post extends AttackEntityEvent {
		public Post(Entity target) {
			super(target);
		}
	}
}
