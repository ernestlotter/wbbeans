package org.webbeans.ui;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * A kind of event a component can send. The client only sends events the server has a listener for;
 * {@link #lazy(int)} events are debounced on the client.
 *
 * @param <E> the event class, constructed from the source component and the client's data map
 */
public final class EventType<E extends Event> {

    private final String name;
    private final BiFunction<Component, Map<String, Object>, E> factory;
    private final int debounceMs;

    private EventType(String name, BiFunction<Component, Map<String, Object>, E> factory, int debounceMs) {
        this.name = name;
        this.factory = factory;
        this.debounceMs = debounceMs;
    }

    public static <E extends Event> EventType<E> of(String name, BiFunction<Component, Map<String, Object>, E> factory) {
        return new EventType<E>(name, factory, 0);
    }

    /** Coalesced on the client: at most one event per {@code debounceMs}. */
    public EventType<E> lazy(int debounceMs) {
        return new EventType<E>(name, factory, debounceMs);
    }

    public String getName() {
        return name;
    }

    public int getDebounceMs() {
        return debounceMs;
    }

    public E create(Component source, Map<String, Object> data) {
        return factory.apply(source, data);
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EventType && ((EventType<?>) o).name.equals(name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
