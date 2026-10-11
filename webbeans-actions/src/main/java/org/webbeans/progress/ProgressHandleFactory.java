package org.webbeans.progress;

import org.webbeans.ui.UISession;

/** Creates {@link ProgressHandle}s bound to the current (or given) session. */
public final class ProgressHandleFactory {

    private ProgressHandleFactory() {
    }

    public static ProgressHandle createHandle(String displayName) {
        return createHandle(displayName, null);
    }

    /** For the current session; without one the handle still works but shows nowhere. */
    public static ProgressHandle createHandle(String displayName, Cancellable cancellable) {
        return createHandle(displayName, cancellable, UISession.current());
    }

    public static ProgressHandle createHandle(String displayName, Cancellable cancellable, UISession session) {
        return new ProgressHandle(session == null ? null : ProgressRegistry.of(session), displayName, cancellable);
    }
}
