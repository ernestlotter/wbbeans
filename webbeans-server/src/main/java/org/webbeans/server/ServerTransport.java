package org.webbeans.server;

import org.webbeans.ui.Transport;

/**
 * What the front end needs from a transport beyond {@link Transport}: whether the client is still there. A tab has
 * one of these at a time, a {@link WebSocketTransport} normally and a {@link PollTransport} for clients that
 * cannot open a WebSocket.
 */
public interface ServerTransport extends Transport {

    /** Closed by the UI, the session or the client; no messages are delivered any more. */
    boolean isClosed();

    /** Milliseconds since the client last gave a sign of life (a message, a poll, a keep-alive). */
    long idleMillis();
}
