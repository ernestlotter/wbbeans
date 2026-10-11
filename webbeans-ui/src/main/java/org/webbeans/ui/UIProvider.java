package org.webbeans.ui;

/**
 * Builds the component tree for a new {@link UI} (browser tab). Found in {@code Lookup.getDefault()}; the
 * window-system module ships the default main-window provider, applications may replace it.
 */
public interface UIProvider {

    void init(UI ui);
}
