package org.webbeans.windows;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Container;
import org.webbeans.ui.Property;

/**
 * The main window's status bar: a message area on the left ({@link StatusDisplayer}) and cells on the right
 * (its children, e.g. progress, caret position, registered under {@code StatusBar} in the registry).
 */
@Renderer(id = "wb.StatusBar", resource = "org/webbeans/windows/client/statusbar.js")
public final class StatusBar extends Container {

    public static final Property<String> TEXT = Property.of("text", String.class, "");

    public StatusBar() {
        super("wb.StatusBar");
    }

    public StatusBar setText(String text) {
        set(TEXT, text == null ? "" : text);
        return this;
    }

    public String getText() {
        return get(TEXT);
    }
}
