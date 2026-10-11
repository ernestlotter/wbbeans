package org.webbeans.ui;

import java.util.Map;

/** A committed value change of a {@link ValueComponent}. */
public class ChangeEvent<T> extends Event {

    private final T value;
    private final T oldValue;
    private final boolean valid;

    public ChangeEvent(Component source, Map<String, Object> data, T value, T oldValue, boolean valid) {
        super(source, data);
        this.value = value;
        this.oldValue = oldValue;
        this.valid = valid;
    }

    public T getValue() {
        return value;
    }

    public T getOldValue() {
        return oldValue;
    }

    /** False when the component's validator rejected the value (the ERROR property is set). */
    public boolean isValid() {
        return valid;
    }
}
