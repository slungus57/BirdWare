package com.birdware.module;

import com.birdware.BirdWare;
import com.birdware.event.events.ModuleToggleEvent;
import com.birdware.setting.BooleanSetting;
import com.birdware.setting.KeybindSetting;
import com.birdware.setting.ModeSetting;
import com.birdware.setting.Setting;
import com.birdware.setting.SettingGroup;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for every BirdWare module.
 *
 * <h2>Lifecycle</h2>
 * Enabling subscribes the module to the {@link com.birdware.event.EventBus} (so its {@code @Subscribe} methods start
 * receiving events) and then calls {@link #onEnable()}. Disabling calls {@link #onDisable()} and unsubscribes.
 * Disabled modules therefore never process events. Both callbacks are exception-isolated.
 *
 * <h2>Settings</h2>
 * Declare settings as fields: {@code private final NumberSetting range = add(new NumberSetting(...));} or put them in
 * a named group: {@code private final SettingGroup sgTargets = group("Targets");} then {@code sgTargets.add(...)}.
 * Every module also owns a trailing "Module" group with its keybind, bind mode, ArrayList visibility and toggle
 * notification preference.
 *
 * <h2>Cleanup</h2>
 * {@link #onDisable()} must release everything the module acquired: rotations, packet buffers, held keys, hotbar
 * slots, targets. The manager also calls {@link #onWorldLeave()} on disconnect/world change while enabled.
 */
public abstract class Module {
	protected static final Minecraft mc = Minecraft.getInstance();

	private final String name;
	private final String description;
	private final Category category;
	private final List<SettingGroup> groups = new ArrayList<>();
	private final SettingGroup moduleGroup = new SettingGroup("Module", false);
	protected final SettingGroup sgGeneral;

	private final KeybindSetting keybind;
	private final ModeSetting bindMode;
	private final BooleanSetting drawn;
	private final BooleanSetting toggleNotify;

	private boolean enabled;
	private int errorCount;

	protected Module(String name, String description, Category category) {
		this.name = name;
		this.description = description;
		this.category = category;
		this.sgGeneral = group("General");
		this.keybind = moduleGroup.add(new KeybindSetting("Keybind", "Key or mouse button that toggles " + name + ".", KeybindSetting.Bind.NONE));
		this.bindMode = moduleGroup.add(new ModeSetting("Bind Mode", "Toggle: press to switch on/off. Hold: active only while the key is held.", "Toggle", "Toggle", "Hold"));
		this.drawn = moduleGroup.add(new BooleanSetting("Show In ArrayList", "Show this module in the HUD ArrayList while enabled.", true));
		this.toggleNotify = moduleGroup.add(new BooleanSetting("Toggle Notification", "Show a notification when this module is toggled.", true));
	}

	// ------------------------------------------------------------------------------------------------ settings

	/** Adds a setting to the General group. */
	protected <S extends Setting<?>> S add(S setting) {
		return sgGeneral.add(setting);
	}

	/** Creates (or returns) a named settings group. Groups render in declaration order; General is always first. */
	protected SettingGroup group(String groupName) {
		for (SettingGroup g : groups) {
			if (g.getName().equalsIgnoreCase(groupName)) return g;
		}
		SettingGroup g = new SettingGroup(groupName, groups.isEmpty());
		groups.add(g);
		return g;
	}

	/** All groups including the trailing "Module" group, skipping empty ones. */
	public List<SettingGroup> getGroups() {
		List<SettingGroup> all = new ArrayList<>(groups.size() + 1);
		for (SettingGroup g : groups) {
			if (!g.getSettings().isEmpty()) all.add(g);
		}
		all.add(moduleGroup);
		return Collections.unmodifiableList(all);
	}

	/** Every setting of this module in display order. */
	public List<Setting<?>> getSettings() {
		List<Setting<?>> list = new ArrayList<>();
		for (SettingGroup g : getGroups()) list.addAll(g.getSettings());
		return list;
	}

	/** Finds a setting by name or id (case/space-insensitive), searching all groups. */
	public Setting<?> getSetting(String nameOrId) {
		for (SettingGroup g : getGroups()) {
			Setting<?> s = g.get(nameOrId);
			if (s != null) return s;
		}
		return null;
	}

	public void resetSettings() {
		for (SettingGroup g : getGroups()) {
			if (g != moduleGroup) g.resetAll();
		}
	}

	// ------------------------------------------------------------------------------------------------ state

	public final void toggle() {
		setEnabled(!enabled);
	}

	public final void enable() {
		setEnabled(true);
	}

	public final void disable() {
		setEnabled(false);
	}

	/** Changes the enabled state; no-op if unchanged. Always call on the client thread. */
	public final void setEnabled(boolean enable) {
		if (enable == enabled) return;
		applyState(enable);
		BirdWare.get().events().post(new ModuleToggleEvent(this, enable));
	}

	/** Sets the state without posting a {@link ModuleToggleEvent} (used while loading configs). */
	public final void setEnabledSilently(boolean enable) {
		if (enable == enabled) return;
		applyState(enable);
	}

	private void applyState(boolean enable) {
		if (enable) {
			enabled = true;
			errorCount = 0;
			BirdWare.get().events().subscribe(this);
			try {
				onEnable();
			} catch (Throwable t) {
				BirdWare.LOGGER.error("Failed to enable module {}", name, t);
			}
		} else {
			enabled = false;
			try {
				onDisable();
			} catch (Throwable t) {
				BirdWare.LOGGER.error("Failed to cleanly disable module {}", name, t);
			}
			BirdWare.get().events().unsubscribe(this);
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	/** Called after the module is enabled and subscribed. The player/level may be null (e.g. in the main menu). */
	protected void onEnable() {
	}

	/** Called before the module is unsubscribed. Must restore every piece of state the module changed. */
	protected void onDisable() {
	}

	/**
	 * Called (while enabled) when the player leaves a world: disconnect, world/dimension change. Clear any world-bound
	 * state (targets, cached positions, packet buffers). The module stays enabled.
	 */
	public void onWorldLeave() {
	}

	/** Suffix shown dimmed after the name in the ArrayList (e.g. the active mode). Null for none. */
	public String getInfo() {
		return null;
	}

	/** Called by the event bus error handler; after repeated failures the module disables itself. */
	public int incrementErrors() {
		return ++errorCount;
	}

	public void resetErrors() {
		errorCount = 0;
	}

	// ------------------------------------------------------------------------------------------------ persistence

	/** Extra per-profile data that is not a setting (e.g. HUD positions). Default: nothing. */
	public void saveData(JsonObject data) {
	}

	/** Counterpart of {@link #saveData(JsonObject)}. Must tolerate missing/invalid entries. */
	public void loadData(JsonObject data) {
	}

	// ------------------------------------------------------------------------------------------------ accessors

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public Category getCategory() {
		return category;
	}

	public KeybindSetting getKeybind() {
		return keybind;
	}

	public boolean isHoldBind() {
		return bindMode.is("Hold");
	}

	public ModeSetting getBindMode() {
		return bindMode;
	}

	public boolean isDrawn() {
		return drawn.get();
	}

	public BooleanSetting getDrawnSetting() {
		return drawn;
	}

	public boolean shouldNotifyToggle() {
		return toggleNotify.get();
	}

	public SettingGroup getModuleGroup() {
		return moduleGroup;
	}

	/** Lowercase id without spaces, used by commands and config files. */
	public String getId() {
		return Setting.idOf(name);
	}

	/** True when a player and level exist (i.e. in game). */
	protected static boolean inGame() {
		return mc.player != null && mc.level != null;
	}

	@Override
	public String toString() {
		return name;
	}
}
