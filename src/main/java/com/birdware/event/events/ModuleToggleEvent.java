package com.birdware.event.events;

import com.birdware.event.Event;
import com.birdware.module.Module;

/** Posted after a module was enabled or disabled by the user (keybind, GUI, command). Not posted for config loads. */
public final class ModuleToggleEvent extends Event {
	private final Module module;
	private final boolean enabled;

	public ModuleToggleEvent(Module module, boolean enabled) {
		this.module = module;
		this.enabled = enabled;
	}

	public Module getModule() {
		return module;
	}

	public boolean isEnabled() {
		return enabled;
	}
}
