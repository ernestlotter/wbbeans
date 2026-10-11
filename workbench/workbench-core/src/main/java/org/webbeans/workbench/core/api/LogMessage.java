package org.webbeans.workbench.core.api;

import java.time.Instant;

/**
 * A line for the Output window, published on the session bus ({@code UISession.publish(new LogMessage(...))})
 * by any Workbench module; every tab of the user shows it. The one shared type of the application.
 */
public final class LogMessage {

    public enum Level { INFO, WARNING, ERROR }

    private final Instant time = Instant.now();
    private final Level level;
    private final String source;
    private final String text;

    public LogMessage(String source, String text) {
        this(Level.INFO, source, text);
    }

    public LogMessage(Level level, String source, String text) {
        this.level = level == null ? Level.INFO : level;
        this.source = source;
        this.text = text;
    }

    public Instant getTime() {
        return time;
    }

    public Level getLevel() {
        return level;
    }

    public String getSource() {
        return source;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return "[" + source + "] " + text;
    }
}
