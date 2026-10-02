package com.birdware.event;

/**
 * Base type for everything that travels over the {@link EventBus}.
 * <p>
 * Events are dispatched by exact class: a listener for {@code TickEvent.Pre} does not receive
 * {@code TickEvent.Post}. Event instances that are posted very frequently (render, tick, packet)
 * should be reused by the poster instead of allocated per call where that is safe.
 */
public abstract class Event {
}
