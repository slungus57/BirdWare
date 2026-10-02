package com.birdware.notification;

import com.birdware.render.Icon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Queue of on-screen notifications. Thread safe for posting (network callbacks may notify); rendering is done by
 * the Notifications HUD module which calls {@link #getActive()} every frame.
 */
public final class NotificationManager {
	public static final int MAX_VISIBLE = 6;

	private final List<Notification> active = new ArrayList<>();
	private volatile boolean enabled = true;
	private volatile long defaultDurationMs = 2500;

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setDefaultDuration(long ms) {
		this.defaultDurationMs = Math.max(500, ms);
	}

	public long getDefaultDuration() {
		return defaultDurationMs;
	}

	public void info(String title, String message) {
		push(title, message, Notification.Type.INFO, null, defaultDurationMs, null);
	}

	public void success(String title, String message) {
		push(title, message, Notification.Type.SUCCESS, null, defaultDurationMs, null);
	}

	public void warning(String title, String message) {
		push(title, message, Notification.Type.WARNING, null, defaultDurationMs + 1000, null);
	}

	public void error(String title, String message) {
		push(title, message, Notification.Type.ERROR, null, defaultDurationMs + 2000, null);
	}

	/**
	 * Shows a notification.
	 * @param category notifications with the same non-null category replace each other instead of stacking
	 */
	public synchronized Notification push(String title, String message, Notification.Type type, Icon icon, long durationMs, String category) {
		if (!enabled) return null;
		if (category != null) {
			active.removeIf(n -> category.equals(n.getCategory()) && !n.isExpired());
		}
		Notification notification = new Notification(title, message, type, icon, durationMs, category);
		active.add(notification);
		while (active.size() > MAX_VISIBLE * 3) active.remove(0);
		return notification;
	}

	/** Snapshot of live notifications, oldest first; dead ones are pruned. */
	public synchronized List<Notification> getActive() {
		active.removeIf(Notification::isDead);
		if (active.isEmpty()) return Collections.emptyList();
		return new ArrayList<>(active);
	}

	public synchronized void clear() {
		active.clear();
	}
}
