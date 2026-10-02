package com.birdware.event;

import com.birdware.BirdWare;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Annotation driven, copy-on-write event bus.
 * <ul>
 *     <li>Listener invokers are generated once per method with {@link LambdaMetafactory}, so dispatch is a plain
 *     interface call instead of reflection.</li>
 *     <li>Dispatch arrays are immutable snapshots, so {@link #post(Event)} is safe from any thread (packet events are
 *     posted from the network thread) and listeners may (un)subscribe while an event is being dispatched.</li>
 *     <li>Every listener invocation is isolated: an exception in one module is reported to the
 *     {@link ListenerErrorHandler} and never propagates into Minecraft's code.</li>
 * </ul>
 */
public final class EventBus {
	private static final Listener[] EMPTY = new Listener[0];
	private static final Comparator<Listener> ORDER = Comparator.comparingInt(Listener::priority).reversed();

	private final Map<Class<?>, Listener[]> dispatch = new ConcurrentHashMap<>();
	private final Map<Object, List<Listener>> bySubscriber = new IdentityHashMap<>();
	private final Map<Class<?>, List<MethodTemplate>> templateCache = new ConcurrentHashMap<>();
	private volatile ListenerErrorHandler errorHandler = (listener, event, error) ->
		BirdWare.LOGGER.error("Listener {} failed while handling {}", listener.describe(), event.getClass().getSimpleName(), error);

	/** Receives exceptions thrown by listeners. */
	@FunctionalInterface
	public interface ListenerErrorHandler {
		void onError(Listener listener, Event event, Throwable error);
	}

	public void setErrorHandler(ListenerErrorHandler handler) {
		this.errorHandler = handler;
	}

	/**
	 * Registers every {@link Subscribe} method declared on the subscriber's class hierarchy.
	 * Subscribing an already subscribed object is a no-op.
	 */
	public void subscribe(Object subscriber) {
		List<MethodTemplate> templates = templatesFor(subscriber.getClass());
		synchronized (this) {
			if (bySubscriber.containsKey(subscriber)) return;
			List<Listener> created = new ArrayList<>(templates.size());
			for (MethodTemplate template : templates) {
				created.add(new Listener(subscriber, template.eventType, template.priority, template.receiveCancelled,
					template.name, event -> template.invoker.accept(subscriber, event)));
			}
			bySubscriber.put(subscriber, created);
			for (Listener listener : created) insert(listener);
		}
	}

	/** Removes every listener registered for the subscriber. Unknown subscribers are ignored. */
	public void unsubscribe(Object subscriber) {
		synchronized (this) {
			List<Listener> removed = bySubscriber.remove(subscriber);
			if (removed == null) return;
			for (Listener listener : removed) remove(listener);
		}
	}

	public boolean isSubscribed(Object subscriber) {
		synchronized (this) {
			return bySubscriber.containsKey(subscriber);
		}
	}

	/**
	 * Registers a single functional listener. The returned handle can be passed to {@link #unregister(Listener)}.
	 * The {@code owner} is used for error reporting and may be {@code null}.
	 */
	public <T extends Event> Listener listen(Class<T> type, int priority, Object owner, Consumer<T> consumer) {
		@SuppressWarnings("unchecked")
		Consumer<Event> cast = (Consumer<Event>) consumer;
		Listener listener = new Listener(owner, type, priority, true, "lambda", cast);
		synchronized (this) {
			insert(listener);
		}
		return listener;
	}

	public void unregister(Listener listener) {
		synchronized (this) {
			remove(listener);
		}
	}

	/** True when at least one listener exists for the exact event type. Use to skip building expensive events. */
	public boolean hasListeners(Class<? extends Event> type) {
		Listener[] listeners = dispatch.get(type);
		return listeners != null && listeners.length > 0;
	}

	/** Dispatches the event to every listener of its exact class, highest priority first. Returns the event. */
	public <T extends Event> T post(T event) {
		Listener[] listeners = dispatch.get(event.getClass());
		if (listeners == null) return event;
		CancellableEvent cancellable = event instanceof CancellableEvent c ? c : null;
		for (Listener listener : listeners) {
			if (cancellable != null && !listener.receiveCancelled && cancellable.isCancelled()) continue;
			try {
				listener.invoker.accept(event);
			} catch (VirtualMachineError fatal) {
				throw fatal;
			} catch (Throwable error) {
				errorHandler.onError(listener, event, error);
			}
		}
		return event;
	}

	private void insert(Listener listener) {
		Listener[] current = dispatch.getOrDefault(listener.eventType, EMPTY);
		Listener[] next = Arrays.copyOf(current, current.length + 1);
		next[current.length] = listener;
		Arrays.sort(next, ORDER);
		dispatch.put(listener.eventType, next);
	}

	private void remove(Listener listener) {
		Listener[] current = dispatch.get(listener.eventType);
		if (current == null) return;
		int index = -1;
		for (int i = 0; i < current.length; i++) {
			if (current[i] == listener) {
				index = i;
				break;
			}
		}
		if (index < 0) return;
		if (current.length == 1) {
			dispatch.remove(listener.eventType);
			return;
		}
		Listener[] next = new Listener[current.length - 1];
		System.arraycopy(current, 0, next, 0, index);
		System.arraycopy(current, index + 1, next, index, current.length - index - 1);
		dispatch.put(listener.eventType, next);
	}

	private List<MethodTemplate> templatesFor(Class<?> type) {
		return templateCache.computeIfAbsent(type, EventBus::scan);
	}

	private static List<MethodTemplate> scan(Class<?> type) {
		List<MethodTemplate> templates = new ArrayList<>();
		for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
			for (Method method : c.getDeclaredMethods()) {
				Subscribe annotation = method.getAnnotation(Subscribe.class);
				if (annotation == null) continue;
				if (Modifier.isStatic(method.getModifiers())) {
					throw new IllegalStateException("@Subscribe method must not be static: " + method);
				}
				Class<?>[] params = method.getParameterTypes();
				if (params.length != 1 || !Event.class.isAssignableFrom(params[0]) || method.getReturnType() != void.class) {
					throw new IllegalStateException("@Subscribe method must be 'void name(SomeEvent event)': " + method);
				}
				templates.add(new MethodTemplate(params[0], annotation.priority(), annotation.receiveCancelled(),
					c.getSimpleName() + "#" + method.getName(), createInvoker(c, method, params[0])));
			}
		}
		return List.copyOf(templates);
	}

	@SuppressWarnings("unchecked")
	private static BiConsumer<Object, Object> createInvoker(Class<?> owner, Method method, Class<?> eventType) {
		try {
			MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
			MethodHandle handle = lookup.unreflect(method);
			CallSite site = LambdaMetafactory.metafactory(lookup, "accept",
				MethodType.methodType(BiConsumer.class),
				MethodType.methodType(void.class, Object.class, Object.class),
				handle,
				MethodType.methodType(void.class, owner, eventType));
			return (BiConsumer<Object, Object>) site.getTarget().invoke();
		} catch (Throwable lambdaFailure) {
			// Fall back to a bound method handle; still far cheaper than Method#invoke.
			try {
				method.setAccessible(true);
				MethodHandle handle = MethodHandles.lookup().unreflect(method)
					.asType(MethodType.methodType(void.class, Object.class, Object.class));
				return (target, event) -> {
					try {
						handle.invokeExact(target, event);
					} catch (RuntimeException | Error e) {
						throw e;
					} catch (Throwable t) {
						throw new RuntimeException(t);
					}
				};
			} catch (IllegalAccessException e) {
				throw new IllegalStateException("Cannot access listener " + method, e);
			}
		}
	}

	private record MethodTemplate(Class<?> eventType, int priority, boolean receiveCancelled, String name,
								  BiConsumer<Object, Object> invoker) {
	}

	/** A registered listener. Instances are immutable and compared by identity. */
	public static final class Listener {
		private final Object owner;
		private final Class<?> eventType;
		private final int priority;
		private final boolean receiveCancelled;
		private final String name;
		private final Consumer<Event> invoker;

		Listener(Object owner, Class<?> eventType, int priority, boolean receiveCancelled, String name, Consumer<Event> invoker) {
			this.owner = owner;
			this.eventType = eventType;
			this.priority = priority;
			this.receiveCancelled = receiveCancelled;
			this.name = name;
			this.invoker = invoker;
		}

		public Object owner() {
			return owner;
		}

		public int priority() {
			return priority;
		}

		public Class<?> eventType() {
			return eventType;
		}

		public String describe() {
			return name + (owner != null ? " (" + owner.getClass().getSimpleName() + ")" : "");
		}
	}
}
