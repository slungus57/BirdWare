package com.birdware.core.target;

import com.birdware.setting.BooleanSetting;
import com.birdware.setting.EnumSetting;
import com.birdware.setting.NumberSetting;
import com.birdware.setting.SettingGroup;

/**
 * Standard set of targeting settings that combat/visual modules add to one of their groups, so every module exposes
 * the same, consistently named filters. Call {@link #apply(TargetFilter)} before querying the TargetManager.
 *
 * <pre>
 * private final TargetSettings targets = new TargetSettings(group("Targets"), 4.2, 6.0, true);
 * ...
 * targets.apply(filter);
 * LivingEntity best = BirdWare.get().targets().findBest(filter, targets.sort());
 * </pre>
 */
public final class TargetSettings {
	public final BooleanSetting players;
	public final BooleanSetting hostiles;
	public final BooleanSetting neutrals;
	public final BooleanSetting passives;
	public final BooleanSetting villagers;
	public final BooleanSetting golems;
	public final BooleanSetting invisibles;
	public final BooleanSetting friends;
	public final BooleanSetting ignoreNaked;
	public final BooleanSetting antiBot;
	public final BooleanSetting teams;
	public final NumberSetting range;
	public final BooleanSetting throughWalls;
	public final NumberSetting wallRange;
	public final NumberSetting fov;
	public final EnumSetting<TargetSort> sort;

	/**
	 * @param group        group to add the settings to
	 * @param defaultRange default range in blocks
	 * @param maxRange     slider maximum
	 * @param withSort     whether to add the priority setting (single-target modules)
	 */
	public TargetSettings(SettingGroup group, double defaultRange, double maxRange, boolean withSort) {
		range = group.add(new NumberSetting("Range", "Maximum distance from your eyes to the target's hitbox.", defaultRange, 1.0, maxRange, 0.1).unit("m"));
		players = group.add(new BooleanSetting("Players", "Target other players.", true));
		hostiles = group.add(new BooleanSetting("Hostiles", "Target hostile mobs (zombies, skeletons, creepers...).", true));
		neutrals = group.add(new BooleanSetting("Neutrals", "Target neutral mobs (endermen, piglins, wolves, bees...).", false));
		passives = group.add(new BooleanSetting("Animals", "Target passive animals and water/ambient creatures.", false));
		villagers = group.add(new BooleanSetting("Villagers", "Target villagers and wandering traders.", false));
		golems = group.add(new BooleanSetting("Golems", "Target iron and snow golems.", false));
		invisibles = group.add(new BooleanSetting("Invisibles", "Target invisible entities.", true));
		friends = group.add(new BooleanSetting("Friends", "Also target players on your friend list.", false));
		ignoreNaked = group.add(new BooleanSetting("Ignore Naked", "Skip players wearing no armor (usually new spawns).", false));
		antiBot = group.add(new BooleanSetting("Anti Bot", "Skip players that are not in the tab list (server NPCs / anti-cheat bots).", true));
		teams = group.add(new BooleanSetting("Ignore Teammates", "Skip players on your scoreboard team.", false));
		throughWalls = group.add(new BooleanSetting("Through Walls", "Allow targets without a clear line of sight.", true));
		wallRange = group.add(new NumberSetting("Wall Range", "Maximum distance for targets behind blocks.", Math.min(3.0, defaultRange), 0.5, maxRange, 0.1).unit("m"))
			.visibleWhen(throughWalls::get);
		fov = group.add(new NumberSetting("FOV", "Only consider targets within this angle of your crosshair.", 360, 10, 360, 5).unit("°"));
		sort = withSort ? group.add(new EnumSetting<>("Priority", "Which valid target to prefer.", TargetSort.DISTANCE)) : null;
	}

	/** Copies the settings into the filter. */
	public TargetFilter apply(TargetFilter filter) {
		filter.players = players.get();
		filter.hostiles = hostiles.get();
		filter.neutrals = neutrals.get();
		filter.passives = passives.get();
		filter.villagers = villagers.get();
		filter.golems = golems.get();
		filter.invisibles = invisibles.get();
		filter.friends = friends.get();
		filter.ignoreNaked = ignoreNaked.get();
		filter.antiBot = antiBot.get();
		filter.ignoreTeammates = teams.get();
		filter.range = range.get();
		filter.requireVisible = !throughWalls.get();
		filter.wallRange = Math.min(wallRange.get(), range.get());
		filter.fov = fov.getFloat();
		return filter;
	}

	public TargetSort sort() {
		return sort != null ? sort.get() : TargetSort.DISTANCE;
	}
}
