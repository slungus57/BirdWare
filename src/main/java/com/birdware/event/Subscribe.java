package com.birdware.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an event listener. The method must take exactly one parameter whose type is a
 * concrete {@link Event} subclass and return {@code void}. Private methods are supported.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {
	/** Higher priority listeners run first. See {@link EventPriority}. */
	int priority() default EventPriority.NORMAL;

	/** When false (default true), the listener is skipped for events that are already cancelled. */
	boolean receiveCancelled() default true;
}
