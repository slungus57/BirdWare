package com.birdware.core.target;

import com.birdware.setting.EnumSetting;

/** Target priority orders. "Best" is the first element after sorting. */
public enum TargetSort implements EnumSetting.Nameable {
	DISTANCE("Distance"),
	HEALTH("Lowest Health"),
	HIGHEST_HEALTH("Highest Health"),
	ARMOR("Lowest Armor"),
	ANGLE("Angle"),
	HURT_TIME("Hurt Time"),
	THREAT("Threat");

	private final String name;

	TargetSort(String name) {
		this.name = name;
	}

	@Override
	public String displayName() {
		return name;
	}
}
