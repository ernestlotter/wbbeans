package org.webbeans.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.Lookup;
import org.webbeans.core.registry.Entry;
import org.webbeans.core.registry.Registry;
import org.webbeans.ui.Registration;
import org.webbeans.ui.UI;

/**
 * Installs the registry folder {@code Shortcuts} on a UI: each reference entry's name is a key in NetBeans
 * syntax ({@link Utilities#keyToClient}), its target the action to run with the UI's global context. The
 * accelerator is recorded on the action so menus can show it.
 */
public final class Shortcuts {

    private static final Logger LOG = Logger.getLogger(Shortcuts.class.getName());
    public static final String SHORTCUTS_ROOT = "Shortcuts";

    private Shortcuts() {
    }

    /** Binds every visible shortcut on the UI; returns a registration removing them all. */
    public static Registration install(final UI ui) {
        final List<Registration> all = new ArrayList<Registration>();
        for (Entry e : Registry.getDefault().folder(SHORTCUTS_ROOT).getEntries()) {
            String key = Utilities.keyToClient(e.getName());
            final Action action = Actions.actionFor(e);
            if (key == null || action == null) {
                LOG.log(Level.WARNING, "Ignoring shortcut entry {0}", e.getPath());
                continue;
            }
            if (action.getValue(Action.ACCELERATOR) == null) {
                action.putValue(Action.ACCELERATOR, key);
            }
            Entry target = e.getTarget();
            all.add(ui.bindShortcut(key, target != null ? target.getPath() : e.getPath(), new Runnable() {
                @Override
                public void run() {
                    Lookup ctx = Utilities.actionsGlobalContext(ui);
                    Action a = action instanceof ContextAwareAction ? ((ContextAwareAction) action).createContextAwareInstance(ctx) : action;
                    try {
                        if (a.isEnabled()) {
                            Actions.invoke(a, null, ctx);
                        }
                    } finally {
                        if (a instanceof ActionPresenters.Disposable) {
                            ((ActionPresenters.Disposable) a).dispose();
                        }
                    }
                }
            }));
        }
        return new Registration() {
            @Override
            public void remove() {
                for (Registration r : all) {
                    r.remove();
                }
            }
        };
    }
}
