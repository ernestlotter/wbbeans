package org.webbeans.actions;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.RequestProcessor;
import org.webbeans.core.registry.Entry;
import org.webbeans.progress.ProgressHandle;
import org.webbeans.progress.ProgressHandleFactory;
import org.webbeans.ui.Resource;
import org.webbeans.ui.UI;

/**
 * Base of the actions {@link Actions} creates: values that come from a registry entry are resolved on every
 * read, so one cached instance serves every session in its own locale; {@link #run} honours {@link #ASYNC}.
 */
abstract class RegistryAction extends AbstractAction {

    private static final Logger LOG = Logger.getLogger(RegistryAction.class.getName());
    private static final RequestProcessor RP = new RequestProcessor("Actions", 4, true);

    private final Entry entry;

    RegistryAction(Entry entry) {
        this.entry = entry;
        if (entry != null) {
            String icon = entry.getString("iconBase");
            if (icon != null && !icon.isEmpty()) {
                putValue(ICON, Resource.of(icon));
            }
            if (Boolean.TRUE.equals(entry.getAttributes().get("asynchronous"))) {
                putValue(ASYNC, Boolean.TRUE);
            }
            if (Boolean.TRUE.equals(entry.getAttributes().get("noIconInMenu"))) {
                putValue(NO_ICON_IN_MENU, Boolean.TRUE);
            }
        }
    }

    Entry getEntry() {
        return entry;
    }

    @Override
    public Object getValue(String key) {
        Object local = super.getValue(key);
        if (local != null || entry == null) {
            return local;
        }
        if (NAME.equals(key)) {
            return entry.getDisplayName();
        }
        if (MENU_TEXT.equals(key) || POPUP_TEXT.equals(key)) {
            String s = entry.getString(key);
            return s == null || s.isEmpty() ? null : s;
        }
        return null;
    }

    /**
     * Runs the action body: inline on the session executor, or on a background thread with a progress handle
     * when {@link #ASYNC} is set (the body uses {@code e.getUI().access(...)} to touch components).
     */
    final void run(final Runnable body, final ActionEvent e) {
        if (!Boolean.TRUE.equals(getValue(ASYNC))) {
            body.run();
            return;
        }
        final UI ui = e.getUI();
        final ProgressHandle handle = ProgressHandleFactory.createHandle(getDisplayName(), null,
                ui != null ? ui.getSession() : null);
        RP.post(new Runnable() {
            @Override
            public void run() {
                handle.start();
                try {
                    body.run();
                } catch (RuntimeException ex) {
                    LOG.log(Level.SEVERE, "Asynchronous action " + getDisplayName() + " failed", ex);
                } finally {
                    handle.finish();
                }
            }
        });
    }
}
