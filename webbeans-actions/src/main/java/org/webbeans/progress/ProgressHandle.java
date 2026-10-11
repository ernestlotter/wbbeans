package org.webbeans.progress;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reports the progress of a task (NetBeans' {@code ProgressHandle}) in the status bar of every tab of the
 * session: indeterminate until {@link #start(int)} or {@link #switchToDeterminate}; any thread may call it.
 * Create with {@link ProgressHandleFactory}.
 */
public final class ProgressHandle {

    private static final Logger LOG = Logger.getLogger(ProgressHandle.class.getName());

    private final ProgressRegistry registry;
    private final Cancellable cancellable;
    private String displayName;
    private String message;
    private int total = -1;
    private int done;
    private boolean started;
    private boolean finished;
    private boolean cancelled;

    ProgressHandle(ProgressRegistry registry, String displayName, Cancellable cancellable) {
        this.registry = registry;
        this.displayName = displayName;
        this.cancellable = cancellable;
    }

    public synchronized String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String name) {
        synchronized (this) {
            displayName = name;
        }
        changed();
    }

    /** The current message (step), or null. */
    public synchronized String getMessage() {
        return message;
    }

    /** Total work units, or -1 while indeterminate. */
    public synchronized int getTotal() {
        return total;
    }

    public synchronized int getDone() {
        return done;
    }

    /** Fraction done, or -1 while indeterminate. */
    public synchronized double getFraction() {
        return total <= 0 ? -1 : Math.min(1.0, done / (double) total);
    }

    public synchronized boolean isStarted() {
        return started;
    }

    public synchronized boolean isFinished() {
        return finished;
    }

    public boolean isCancellable() {
        return cancellable != null;
    }

    public synchronized boolean isCancelled() {
        return cancelled;
    }

    /** Starts indeterminate. */
    public void start() {
        start(0);
    }

    public void start(int workUnits) {
        synchronized (this) {
            if (started) {
                return;
            }
            started = true;
            total = workUnits > 0 ? workUnits : -1;
            done = 0;
        }
        if (registry != null) {
            registry.add(this);
        }
        changed();
    }

    public void progress(int workUnitsDone) {
        synchronized (this) {
            done = workUnitsDone;
        }
        changed();
    }

    public void progress(String msg) {
        synchronized (this) {
            message = msg;
        }
        changed();
    }

    public void progress(String msg, int workUnitsDone) {
        synchronized (this) {
            message = msg;
            done = workUnitsDone;
        }
        changed();
    }

    public void switchToIndeterminate() {
        synchronized (this) {
            total = -1;
        }
        changed();
    }

    public void switchToDeterminate(int workUnits) {
        synchronized (this) {
            total = workUnits > 0 ? workUnits : -1;
            done = Math.min(done, Math.max(0, workUnits));
        }
        changed();
    }

    public void finish() {
        synchronized (this) {
            if (finished) {
                return;
            }
            finished = true;
            if (total > 0) {
                done = total;
            }
        }
        if (registry != null) {
            registry.remove(this);
        }
    }

    /** Asks the task to stop (from the status bar); returns what the {@link Cancellable} said. */
    public boolean cancel() {
        if (cancellable == null) {
            return false;
        }
        boolean ok;
        try {
            ok = cancellable.cancel();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Cancel of " + displayName + " failed", e);
            ok = false;
        }
        if (ok) {
            synchronized (this) {
                cancelled = true;
            }
            changed();
        }
        return ok;
    }

    private void changed() {
        if (registry != null && isStarted() && !isFinished()) {
            registry.changed();
        }
    }
}
