package com.birdware.module;

import com.birdware.render.Icon;

/** Module categories in ClickGUI panel order. */
public enum Category {
	COMBAT("Combat", Icon.COMBAT),
	MOVEMENT("Movement", Icon.MOVEMENT),
	PLAYER("Player", Icon.PLAYER),
	WORLD("World", Icon.WORLD),
	RENDER("Render", Icon.RENDER),
	HUD("HUD", Icon.HUD),
	MISC("Misc", Icon.MISC),
	CLIENT("Client", Icon.CLIENT);

	private final String displayName;
	private final Icon icon;

	Category(String displayName, Icon icon) {
		this.displayName = displayName;
		this.icon = icon;
	}

	public String getDisplayName() {
		return displayName;
	}

	public Icon getIcon() {
		return icon;
	}

	@Override
	public String toString() {
		return displayName;
	}
}
