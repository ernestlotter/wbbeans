package org.webbeans.windows;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Container;

/**
 * The IDE-style page: menu bar, toolbars, the dock and the status bar, stacked. Children are placed by
 * constraint ({@link #MENUBAR}, {@link #TOOLBAR}, {@link #DOCK}, {@link #STATUS}); the menu and toolbar slots are
 * filled by the actions module (step 5).
 */
@Renderer(id = "wb.MainWindow", resource = "org/webbeans/windows/client/mainwindow.js")
public final class MainWindow extends Container {

    public static final String MENUBAR = "menubar";
    public static final String TOOLBAR = "toolbar";
    public static final String DOCK = "dock";
    public static final String STATUS = "status";

    public MainWindow() {
        super("wb.MainWindow");
    }

    public MainWindow setMenuBar(Component c) {
        return replace(MENUBAR, c);
    }

    public MainWindow setToolBar(Component c) {
        return replace(TOOLBAR, c);
    }

    public MainWindow setDock(Component c) {
        return replace(DOCK, c);
    }

    public MainWindow setStatusBar(Component c) {
        return replace(STATUS, c);
    }

    public Component getSlot(String constraint) {
        for (Component c : getChildren()) {
            if (constraint.equals(c.getConstraint())) {
                return c;
            }
        }
        return null;
    }

    private MainWindow replace(String slot, Component c) {
        Component old = getSlot(slot);
        if (old != null) {
            remove(old);
        }
        if (c != null) {
            add(c, slot);
        }
        return this;
    }
}
