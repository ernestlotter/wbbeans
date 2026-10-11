package org.webbeans.actions;

import org.openide.util.Lookup;
import org.webbeans.ui.Component;
import org.webbeans.ui.UI;

/** An invocation of an {@link Action}: who triggered it and in which context. */
public final class ActionEvent {

    private final Component source;
    private final Lookup lookup;
    private final String command;
    private final UI ui;

    /**
     * @param source  the presenter (menu item, button, view) or null for shortcuts and programmatic calls
     * @param lookup  the invocation context: the selection the action applies to; never null
     * @param command an optional command string (dialog buttons use the option)
     */
    public ActionEvent(Component source, Lookup lookup, String command) {
        this.source = source;
        this.lookup = lookup == null ? Lookup.EMPTY : lookup;
        this.command = command;
        this.ui = source != null && source.getUI() != null ? source.getUI() : UI.current();
    }

    public ActionEvent(Component source, Lookup lookup) {
        this(source, lookup, null);
    }

    public Component getSource() {
        return source;
    }

    /** The context the action was invoked with (the selection, the active window's lookup, ...). */
    public Lookup getLookup() {
        return lookup;
    }

    public String getActionCommand() {
        return command;
    }

    /** The UI the invocation came from (also valid on a background thread, use {@code ui.access}). */
    public UI getUI() {
        return ui;
    }

    @Override
    public String toString() {
        return "ActionEvent[" + source + (command != null ? ", " + command : "") + "]";
    }
}
