package org.webbeans.ui;

/**
 * A typed, statically declared component property. Components declare their properties as
 * {@code public static final Property<T>} constants so renderers and the protocol stay type-safe.
 *
 * <p>A {@link #clientOwned()} property is one the browser is the source of truth for (a text field's value,
 * a scroll position): server writes are sent, but values the client reports are stored without echoing
 * a delta back, so typing never fights with the server.
 */
public final class Property<T> {

    private final String name;
    private final Class<T> type;
    private final T defaultValue;
    private final boolean clientOwned;

    private Property(String name, Class<T> type, T defaultValue, boolean clientOwned) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.clientOwned = clientOwned;
    }

    public static <T> Property<T> of(String name, Class<T> type, T defaultValue) {
        if (name == null || name.isEmpty() || name.startsWith("$")) {
            throw new IllegalArgumentException("Invalid property name " + name);
        }
        return new Property<T>(name, type, defaultValue, false);
    }

    public Property<T> clientOwned() {
        return new Property<T>(name, type, defaultValue, true);
    }

    public String getName() {
        return name;
    }

    public Class<T> getType() {
        return type;
    }

    public T getDefault() {
        return defaultValue;
    }

    public boolean isClientOwned() {
        return clientOwned;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Property && ((Property<?>) o).name.equals(name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
