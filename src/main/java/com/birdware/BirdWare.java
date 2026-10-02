package com.birdware;

import com.birdware.config.ConfigManager;
import com.birdware.config.JsonFiles;
import com.birdware.core.InventoryManager;
import com.birdware.core.PacketManager;
import com.birdware.core.RotationManager;
import com.birdware.core.target.TargetManager;
import com.birdware.event.EventBus;
import com.birdware.friend.FriendManager;
import com.birdware.gui.theme.ThemeManager;
import com.birdware.module.Module;
import com.birdware.module.ModuleManager;
import com.birdware.notification.Notification;
import com.birdware.notification.NotificationManager;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Central access point. {@code BirdWare.get().modules()}, {@code .events()}, ... Created once by
 * {@link BirdWareClient}; every manager is available after {@link #init()} returns.
 */
public final class BirdWare {
	public static final String NAME = "BirdWare";
	public static final String MOD_ID = "birdware";
	public static final Logger LOGGER = LoggerFactory.getLogger(NAME);
	private static final int MAX_LISTENER_ERRORS = 10;

	private static BirdWare instance;

	private final String version;
	private final Path root;
	private final EventBus events = new EventBus();
	private final ModuleManager modules = new ModuleManager();
	private final PacketManager packets = new PacketManager();
	private final NotificationManager notifications = new NotificationManager();
	private final RotationManager rotations = new RotationManager();
	private final InventoryManager inventory = new InventoryManager();
	private final TargetManager targets = new TargetManager();
	private final ConfigManager config;
	private final FriendManager friends;
	private final ThemeManager themes;

	private BirdWare() {
		this.version = FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		this.root = FabricLoader.getInstance().getGameDir().resolve(MOD_ID);
		this.config = new ConfigManager(root);
		this.friends = new FriendManager(root);
		this.themes = new ThemeManager(root);
	}

	public static BirdWare get() {
		return instance;
	}

	static BirdWare create() {
		if (instance != null) throw new IllegalStateException("BirdWare already created");
		instance = new BirdWare();
		return instance;
	}

	void init() {
		long start = System.nanoTime();
		events.setErrorHandler(this::onListenerError);
		friends.load();
		events.subscribe(modules);
		rotations.init();
		inventory.init();
		targets.init();
		ModuleRegistry.registerAll(modules);
		modules.finishRegistration();
		config.loadAll();
		LOGGER.info("{} {} initialised with {} modules in {} ms", NAME, version, modules.getModules().size(),
			(System.nanoTime() - start) / 1_000_000);
	}

	/** Saves everything synchronously; called on client shutdown. */
	void shutdown() {
		try {
			for (Module module : modules.getModules()) {
				if (module.isEnabled()) {
					try {
						module.onWorldLeave();
					} catch (Throwable ignored) {
						// Shutting down; best effort only.
					}
				}
			}
			config.saveAll(false);
			JsonFiles.flush();
		} catch (Throwable t) {
			LOGGER.error("Error while saving on shutdown", t);
		}
	}

	private void onListenerError(EventBus.Listener listener, com.birdware.event.Event event, Throwable error) {
		if (listener.owner() instanceof Module module) {
			int count = module.incrementErrors();
			if (count == 1) {
				LOGGER.error("Module {} threw while handling {}", module.getName(), event.getClass().getSimpleName(), error);
			}
			if (count == MAX_LISTENER_ERRORS) {
				LOGGER.error("Disabling module {} after {} errors", module.getName(), count, error);
				// Errors may come from the network thread; state changes always happen on the client thread.
				net.minecraft.client.Minecraft.getInstance().execute(() -> {
					if (!module.isEnabled()) return;
					module.setEnabledSilently(false);
					notifications.push(module.getName(), "Disabled after repeated errors (see log)", Notification.Type.ERROR, null, 5000, null);
				});
			}
		} else {
			LOGGER.error("Listener {} threw while handling {}", listener.describe(), event.getClass().getSimpleName(), error);
		}
	}

	public String version() {
		return version;
	}

	public Path root() {
		return root;
	}

	public EventBus events() {
		return events;
	}

	public ModuleManager modules() {
		return modules;
	}

	public ConfigManager config() {
		return config;
	}

	public FriendManager friends() {
		return friends;
	}

	public ThemeManager themes() {
		return themes;
	}

	public NotificationManager notifications() {
		return notifications;
	}

	public PacketManager packets() {
		return packets;
	}

	public RotationManager rotations() {
		return rotations;
	}

	public InventoryManager inventory() {
		return inventory;
	}

	public TargetManager targets() {
		return targets;
	}
}
