package org.webbeans.ui;

/** Handle returned by {@code addListener}; {@link #remove()} unsubscribes. */
public interface Registration {
    void remove();
}
