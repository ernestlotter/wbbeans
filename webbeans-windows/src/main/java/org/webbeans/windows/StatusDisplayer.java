package org.webbeans.windows;

import org.webbeans.ui.UI;

/**
 * Shows a message in the status bar of the current UI, as NetBeans' {@code StatusDisplayer}. Safe from any
 * thread: the call hops onto the session executor.
 */
public final class StatusDisplayer {

    private final UI ui;
    private final StatusBar bar;
    private volatile String text = "";

    StatusDisplayer(UI ui, StatusBar bar) {
        this.ui = ui;
        this.bar = bar;
    }

    /** The displayer of the current UI, or null when the page has no status bar. */
    public static StatusDisplayer getDefault() {
        UI ui = UI.current();
        return ui == null ? null : ui.getLookup().lookup(StatusDisplayer.class);
    }

    public String getStatusText() {
        return text;
    }

    public void setStatusText(final String t) {
        text = t == null ? "" : t;
        if (ui.getSession().isSessionThread()) {
            bar.setText(text);
        } else {
            ui.access(new Runnable() {
                @Override
                public void run() {
                    bar.setText(text);
                }
            });
        }
    }
}
