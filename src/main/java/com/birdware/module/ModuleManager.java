package com.birdware.module;

import com.birdware.BirdWare;
import com.birdware.event.EventPriority;
import com.birdware.event.Subscribe;
import com.birdware.event.events.GameLeaveEvent;
import com.birdware.event.events.KeyInputEvent;
import com.birdware.event.events.MouseClickEvent;
import com.birdware.event.events.WorldChangeEvent;
import com.birdware.setting.KeybindSetting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of all modules: lookup by class/name/category, keybind dispatch, and world-leave cleanup.
 */
public final class ModuleManager {
	private final Map<Class<? extends Module>, Module> byClass = new IdentityHashMap<>();
	private final Map<String, Module> byId = new LinkedHashMap<>();
	private final Map<Category, List<Module>> byCategory = new EnumMap<>(Category.class);
	private final List<Module> modules = new ArrayList<>();
	private final List<Module> modulesView = Collections.unmodifiableList(modules);

	public ModuleManager() {
		for (Category category : Category.values()) byCategory.put(category, new ArrayList<>());
	}

	public <T extends Module> T register(T module) {
		if (byClass.containsKey(module.getClass())) {
			throw new IllegalStateException("Module registered twice: " + module.getClass().getName());
		}
		if (byId.containsKey(module.getId())) {
			throw new IllegalStateException("Duplicate module name: " + module.getName());
		}
		byClass.put(module.getClass(), module);
		byId.put(module.getId(), module);
		modules.add(module);
		byCategory.get(module.getCategory()).add(module);
		return module;
	}

	/** Sorts every category list alphabetically; call once after registration. */
	public void finishRegistration() {
		for (List<Module> list : byCategory.values()) list.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
		modules.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
	}

	@SuppressWarnings("unchecked")
	public <T extends Module> T get(Class<T> type) {
		return (T) byClass.get(type);
	}

	/** Case and whitespace insensitive lookup by name. */
	public Module get(String name) {
		if (name == null) return null;
		return byId.get(com.birdware.setting.Setting.idOf(name));
	}

	public boolean isEnabled(Class<? extends Module> type) {
		Module module = byClass.get(type);
		return module != null && module.isEnabled();
	}

	public List<Module> getModules() {
		return modulesView;
	}

	public List<Module> getByCategory(Category category) {
		return Collections.unmodifiableList(byCategory.get(category));
	}

	public List<Module> getEnabled() {
		List<Module> list = new ArrayList<>();
		for (Module module : modules) {
			if (module.isEnabled()) list.add(module);
		}
		return list;
	}

	/** Modules whose name, description or category match the query (case insensitive), best matches first. */
	public List<Module> search(String query) {
		String q = query == null ? "" : query.trim().toLowerCase();
		if (q.isEmpty()) return modulesView;
		List<Module> nameStarts = new ArrayList<>();
		List<Module> nameContains = new ArrayList<>();
		List<Module> other = new ArrayList<>();
		String qId = com.birdware.setting.Setting.idOf(q);
		for (Module module : modules) {
			String name = module.getName().toLowerCase();
			if (name.startsWith(q) || (!qId.isEmpty() && module.getId().startsWith(qId))) nameStarts.add(module);
			else if (name.contains(q) || (!qId.isEmpty() && module.getId().contains(qId))) nameContains.add(module);
			else if (module.getDescription().toLowerCase().contains(q)
				|| module.getCategory().getDisplayName().toLowerCase().contains(q)) other.add(module);
		}
		nameStarts.addAll(nameContains);
		nameStarts.addAll(other);
		return nameStarts;
	}

	/** Modules (other than {@code except}) bound to the same key/button. Used for conflict warnings. */
	public List<Module> getConflicts(KeybindSetting.Bind bind, Module except) {
		List<Module> list = new ArrayList<>();
		if (bind == null || !bind.isBound()) return list;
		for (Module module : modules) {
			if (module != except && module.getKeybind().get().equals(bind)) list.add(module);
		}
		return list;
	}

	/** Map of module id to module, for command completion. */
	public Map<String, Module> idMap() {
		return Collections.unmodifiableMap(new HashMap<>(byId));
	}

	public void disableAll() {
		for (Module module : modules) {
			if (module.isEnabled() && module.getCategory() != Category.CLIENT && module.getCategory() != Category.HUD) {
				module.disable();
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ binds

	@Subscribe(priority = EventPriority.HIGH)
	private void onKey(KeyInputEvent event) {
		// Binds only fire in game view: typing in chat, the GUI, or any other screen never toggles modules.
		// Releases are always processed so hold-binds never get stuck when a screen opens mid-hold.
		if (event.isRepeat() || event.getKey() < 0) return;
		if (event.isScreenOpen() && !event.isRelease()) return;
		for (Module module : modules) {
			KeybindSetting.Bind bind = module.getKeybind().get();
			if (!bind.matchesKey(event.getKey())) continue;
			handleBind(module, event.isPress());
		}
	}

	@Subscribe(priority = EventPriority.HIGH)
	private void onMouse(MouseClickEvent event) {
		if (event.isScreenOpen() && !event.isRelease()) return;
		for (Module module : modules) {
			KeybindSetting.Bind bind = module.getKeybind().get();
			if (!bind.matchesMouse(event.getButton())) continue;
			handleBind(module, event.isPress());
		}
	}

	private void handleBind(Module module, boolean press) {
		if (module.isHoldBind()) {
			module.setEnabled(press);
		} else if (press) {
			module.toggle();
		}
	}

	// ------------------------------------------------------------------------------------------------ cleanup

	@Subscribe(priority = EventPriority.HIGHEST)
	private void onWorldChange(WorldChangeEvent event) {
		if (event.getOldLevel() != null) notifyWorldLeave();
	}

	@Subscribe(priority = EventPriority.HIGHEST)
	private void onLeave(GameLeaveEvent event) {
		notifyWorldLeave();
	}

	private void notifyWorldLeave() {
		for (Module module : modules) {
			if (!module.isEnabled()) continue;
			try {
				module.onWorldLeave();
			} catch (Throwable t) {
				BirdWare.LOGGER.error("Module {} failed during world-leave cleanup", module.getName(), t);
			}
		}
	}
}
