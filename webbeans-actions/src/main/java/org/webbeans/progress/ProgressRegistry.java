package org.webbeans.progress;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.webbeans.ui.UISession;

/** The running handles of one session; the status bar cells listen for changes (coalesced on their side). */
public final class ProgressRegistry {

    public interface Listener {
        void progressChanged();
    }

    private final List<ProgressHandle> running = new ArrayList<ProgressHandle>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    private ProgressRegistry() {
    }

    public static ProgressRegistry of(UISession session) {
        synchronized (session) {
            ProgressRegistry r = session.getAttribute(ProgressRegistry.class);
            if (r == null) {
                r = new ProgressRegistry();
                session.setAttribute(ProgressRegistry.class, r);
            }
            return r;
        }
    }

    /** The running handles, oldest first. */
    public List<ProgressHandle> getRunning() {
        synchronized (running) {
            return new ArrayList<ProgressHandle>(running);
        }
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    void add(ProgressHandle h) {
        synchronized (running) {
            running.add(h);
        }
        changed();
    }

    void remove(ProgressHandle h) {
        synchronized (running) {
            running.remove(h);
        }
        changed();
    }

    void changed() {
        for (Listener l : listeners) {
            l.progressChanged();
        }
    }
}
