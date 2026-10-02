package com.birdware.notification;

import com.birdware.render.Icon;
import com.birdware.util.Animation;
import com.birdware.util.Easing;

/**
 * A toast shown by the notification overlay. Created through {@link NotificationManager}.
 */
public final class Notification {
	public enum Type {
		INFO(Icon.INFO),
		SUCCESS(Icon.SUCCESS),
		WARNING(Icon.WARNING),
		ERROR(Icon.ERROR),
		MODULE_ON(Icon.TOGGLE_ON),
		MODULE_OFF(Icon.TOGGLE_OFF);

		private final Icon icon;

		Type(Icon icon) {
			this.icon = icon;
		}

		public Icon icon() {
			return icon;
		}
	}

	private final String title;
	private final String message;
	private final Type type;
	private final Icon icon;
	private final long durationMs;
	private final String category;
	private long createdAt;
	private final Animation slide = new Animation(260, Easing.CUBIC_OUT);
	/** Smoothed vertical slot (managed by the renderer). */
	public float renderY = Float.NaN;

	Notification(String title, String message, Type type, Icon icon, long durationMs, String category) {
		this.title = title;
		this.message = message == null ? "" : message;
		this.type = type;
		this.icon = icon != null ? icon : type.icon();
		this.durationMs = Math.max(500, durationMs);
		this.category = category;
		this.createdAt = System.currentTimeMillis();
		slide.setForward(true);
	}

	public String getTitle() {
		return title;
	}

	public String getMessage() {
		return message;
	}

	public Type getType() {
		return type;
	}

	public Icon getIcon() {
		return icon;
	}

	public long getDurationMs() {
		return durationMs;
	}

	/** Grouping key; a new notification with the same category replaces the old one (e.g. toggling quickly). */
	public String getCategory() {
		return category;
	}

	public long getCreatedAt() {
		return createdAt;
	}

	/** 0..1 elapsed fraction of the display time (drives the progress bar). */
	public float getProgress() {
		return Math.min(1f, (System.currentTimeMillis() - createdAt) / (float) durationMs);
	}

	public boolean isExpired() {
		return System.currentTimeMillis() - createdAt >= durationMs;
	}

	/** Slide-in/out animation: forward while alive, reverses when expired. */
	public Animation getSlide() {
		if (isExpired() && slide.isForward()) slide.setForward(false);
		return slide;
	}

	/** True once expired and fully slid out. */
	public boolean isDead() {
		return isExpired() && getSlide().isHidden();
	}

	void restart() {
		createdAt = System.currentTimeMillis();
		slide.setForward(true);
	}
}
