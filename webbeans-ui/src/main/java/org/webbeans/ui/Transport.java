package org.webbeans.ui;

/**
 * The server side of one client connection (a WebSocket, a long-poll queue, a test recorder). Implementations
 * must accept {@link #send} from the session executor thread and deliver in order.
 */
public interface Transport {

    /** Queues/sends one downstream message (already JSON). */
    void send(String json);

    /** Called when the UI is closed; the transport should release the client (close socket, end polls). */
    void close();
}
