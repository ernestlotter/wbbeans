package org.webbeans.ui;

import java.util.Collection;

/**
 * The live sessions of the server, for administrative modules ({@code Lookup.getDefault().lookup(SessionDirectory.class)};
 * the server front end registers the implementation). Sessions carry {@code "remoteAddress"} as a string attribute.
 */
public interface SessionDirectory {

    /** Name of the string attribute with the client address. */
    String REMOTE_ADDRESS = "remoteAddress";

    /** All active sessions (a snapshot). */
    Collection<UISession> getSessions();

    /** Ends a session (logs the user out of every tab); returns false if it was not found. */
    boolean invalidate(String sessionId);
}
