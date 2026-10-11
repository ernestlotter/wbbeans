package org.webbeans.workbench.core.api;

import org.webbeans.ui.UISession;

/** Convenience: {@code Log.info("files", "Saved README.md")} publishes a {@link LogMessage} to the current session. */
public final class Log {

    private Log() {
    }

    public static void info(String source, String text) {
        publish(new LogMessage(LogMessage.Level.INFO, source, text));
    }

    public static void warn(String source, String text) {
        publish(new LogMessage(LogMessage.Level.WARNING, source, text));
    }

    public static void error(String source, String text) {
        publish(new LogMessage(LogMessage.Level.ERROR, source, text));
    }

    public static void publish(LogMessage m) {
        UISession s = UISession.current();
        if (s != null) {
            s.publish(m);
        }
    }
}
