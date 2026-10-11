package org.webbeans.ui;

/** An event listener; runs on the session executor. */
public interface Listener<E extends Event> {
    void onEvent(E event);
}
