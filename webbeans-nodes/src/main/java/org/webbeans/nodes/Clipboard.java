package org.webbeans.nodes;

import java.util.ArrayList;
import java.util.List;

import org.webbeans.ui.UISession;

/** The per-session clipboard of the explorer (copy/cut/paste between views and tabs of one user). */
public final class Clipboard {

    public interface Listener {
        void clipboardChanged(Clipboard c);
    }

    private Transferable contents;
    private boolean cut;
    private final List<Listener> listeners = new ArrayList<Listener>();

    /** The clipboard of the current session (created on first use). */
    public static synchronized Clipboard getDefault() {
        UISession s = UISession.current();
        if (s == null) {
            throw new IllegalStateException("No current session");
        }
        Clipboard c = s.getAttribute(Clipboard.class);
        if (c == null) {
            c = new Clipboard();
            s.setAttribute(Clipboard.class, c);
        }
        return c;
    }

    public synchronized Transferable getContents() {
        return contents;
    }

    /** Whether the contents came from a cut (the source will be removed on paste). */
    public synchronized boolean isCut() {
        return cut;
    }

    public void setContents(Transferable t, boolean cut) {
        synchronized (this) {
            this.contents = t;
            this.cut = cut;
        }
        for (Listener l : new ArrayList<Listener>(listeners)) {
            l.clipboardChanged(this);
        }
    }

    public synchronized void addListener(Listener l) {
        listeners.add(l);
    }

    public synchronized void removeListener(Listener l) {
        listeners.remove(l);
    }
}
