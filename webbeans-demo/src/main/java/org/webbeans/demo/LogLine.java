package org.webbeans.demo;

/** A session-bus event: something happened that the Events window should show (all tabs of the user see it). */
public final class LogLine {

    public final String text;

    public LogLine(String text) {
        this.text = text;
    }
}
