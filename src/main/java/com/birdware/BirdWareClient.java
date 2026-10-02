package com.birdware;

import com.birdware.event.EventBus;
import com.birdware.event.events.ChatSendEvent;
import com.birdware.event.events.ClientTickEvent;
import com.birdware.event.events.GameJoinEvent;
import com.birdware.event.events.GameLeaveEvent;
import com.birdware.event.events.Render2DEvent;
import com.birdware.event.events.TickEvent;
import com.birdware.event.events.WorldChangeEvent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;

/**
 * Fabric entrypoint. Bridges Fabric API callbacks onto the BirdWare {@link EventBus}; the remaining hooks live in
 * {@code com.birdware.mixin.core}.
 */
public final class BirdWareClient implements ClientModInitializer {
	private static final long AUTOSAVE_INTERVAL_MS = 5 * 60 * 1000L;

	private final Render2DEvent render2DEvent = new Render2DEvent();
	private ClientLevel lastLevel;
	private long lastAutosave = System.currentTimeMillis();

	@Override
	public void onInitializeClient() {
		BirdWare birdWare = BirdWare.create();
		birdWare.init();
		com.birdware.render.Render3D.init();
		EventBus bus = birdWare.events();

		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			checkLevelChange(client, bus);
			bus.post(ClientTickEvent.Pre.INSTANCE);
			if (client.player != null && client.level != null) bus.post(TickEvent.Pre.INSTANCE);
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player != null && client.level != null) bus.post(TickEvent.Post.INSTANCE);
			bus.post(ClientTickEvent.Post.INSTANCE);
			long now = System.currentTimeMillis();
			if (now - lastAutosave >= AUTOSAVE_INTERVAL_MS) {
				lastAutosave = now;
				birdWare.config().saveAll(true);
			}
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
			runOnClient(client, () -> bus.post(new GameJoinEvent())));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
			runOnClient(client, () -> {
				bus.post(new GameLeaveEvent());
				birdWare.config().saveAll(true);
			}));

		ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
			ChatSendEvent event = bus.post(new ChatSendEvent(message));
			return !event.isCancelled();
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(BirdWare.MOD_ID, "hud"),
			(graphics, deltaTracker) -> bus.post(render2DEvent.set(graphics, deltaTracker.getGameTimeDeltaPartialTick(true))));

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> birdWare.shutdown());
	}

	private void checkLevelChange(Minecraft client, EventBus bus) {
		ClientLevel level = client.level;
		if (level != lastLevel) {
			ClientLevel old = lastLevel;
			lastLevel = level;
			bus.post(new WorldChangeEvent(old, level));
		}
	}

	private static void runOnClient(Minecraft client, Runnable task) {
		if (client.isSameThread()) task.run();
		else client.execute(task);
	}
}
