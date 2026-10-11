package org.webbeans.server;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.webbeans.core.json.Json;
import org.webbeans.ui.UI;

/**
 * One WebSocket connection of one browser tab: the {@link org.webbeans.ui.Transport} the UI sends its messages
 * through, and the {@link WebSocketListener} that receives the client's. Created by the
 * {@link org.webbeans.server.handlers.WebSocketHandler} once the upgrade request has been authenticated.
 * <p>
 * Upstream text frames are JSON objects with a {@code t}: {@code hello} ({@code lastSeq}) binds the transport to
 * the UI and replays or re-initialises, {@code ev} ({@code events}) dispatches events and is answered with an
 * {@code ack}, {@code ping} keeps the connection alive and is answered with {@code pong}, {@code bye} closes
 * the UI. Downstream frames are the UI's messages ({@code init}, {@code delta}, {@code nav}, {@code err}) plus
 * {@code ack}, {@code pong} and {@code gone} (the UI or session no longer exists; close code {@link #CLOSE_GONE}).
 * Each frame carries exactly one message.
 */
public final class WebSocketTransport implements ServerTransport, WebSocketListener {

    private static final Logger LOG = Logger.getLogger(WebSocketTransport.class.getName());

    /** Close codes (4000–4999 are application defined): not authenticated, bad token, UI/session gone. */
    public static final int CLOSE_UNAUTHENTICATED = 4401;
    public static final int CLOSE_BAD_TOKEN = 4403;
    public static final int CLOSE_GONE = 4410;

    private final HttpSession httpSession;
    private final UI ui;
    private volatile Session socket;
    private volatile boolean closed;
    private volatile boolean bound;
    private volatile long lastActivity = System.currentTimeMillis();

    public WebSocketTransport(HttpSession httpSession, UI ui) {
        this.httpSession = httpSession;
        this.ui = ui;
    }

    public UI getUI() {
        return ui;
    }

    // ---- Transport ----------------------------------------------------------------------------------------------

    @Override
    public void send(String json) {
        Session s = socket;
        if (closed || s == null || !s.isOpen()) {
            return;
        }
        try {
            s.getRemote().sendString(json, new WriteCallback() {
                @Override
                public void writeSuccess() {
                }

                @Override
                public void writeFailed(Throwable x) {
                    LOG.log(Level.FINE, "WebSocket write failed for " + ui, x);
                }
            });
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "WebSocket send failed for " + ui, e);
        }
    }

    /** Closes the socket (the UI was closed, the session ended, or a newer connection replaced this one). */
    @Override
    public void close() {
        close(StatusCode.NORMAL, "closed");
    }

    private void close(int code, String reason) {
        if (closed) {
            return;
        }
        closed = true;
        Session s = socket;
        if (s != null && s.isOpen()) {
            try {
                s.close(code, reason);
            } catch (RuntimeException e) {
                LOG.log(Level.FINE, "WebSocket close failed", e);
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public long idleMillis() {
        return System.currentTimeMillis() - lastActivity;
    }

    // ---- WebSocketListener ----------------------------------------------------------------------------------------

    @Override
    public void onWebSocketConnect(Session session) {
        this.socket = session;
        lastActivity = System.currentTimeMillis();
        if (closed) {
            session.close(StatusCode.NORMAL, "closed");
        }
    }

    @Override
    public void onWebSocketText(String message) {
        lastActivity = System.currentTimeMillis();
        if (closed) {
            return;
        }
        Map<String, Object> body;
        try {
            body = Json.parseObject(message);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Bad WebSocket message from {0}: {1}", new Object[] {httpSession.getRemoteAddress(), e.getMessage()});
            close(StatusCode.BAD_DATA, "bad message");
            return;
        }
        String t = Json.string(body, "t");
        if (!httpSession.isValid() || ui.getState() == UI.State.CLOSED) {
            gone();
            return;
        }
        if ("hello".equals(t)) {
            hello(Json.integer(body, "lastSeq", -1));
        } else if ("ev".equals(t)) {
            events(Json.array(body, "events"));
        } else if ("ping".equals(t)) {
            httpSession.touch();
            send("{\"t\":\"pong\"}");
        } else if ("bye".equals(t)) {
            httpSession.removeTransport(ui.getId(), this);
            ui.close();
            close(StatusCode.NORMAL, "bye");
        } else {
            LOG.log(Level.FINE, "Unknown WebSocket message type {0}", t);
        }
    }

    private void hello(final long lastSeq) {
        ServerTransport old = httpSession.replaceTransport(ui.getId(), this);
        if (old != null && old != this) {
            old.close();
        }
        bound = true;
        ui.access(new Runnable() {
            @Override
            public void run() {
                try {
                    ui.connect(WebSocketTransport.this, lastSeq);
                } catch (IllegalStateException e) {
                    gone();
                }
            }
        });
    }

    private void events(final List<Object> events) {
        if (!bound) {
            send("{\"t\":\"err\",\"message\":\"hello first\"}");
            return;
        }
        if (events == null || events.isEmpty()) {
            return;
        }
        long last = -1;
        for (Object o : events) {
            if (o instanceof Map) {
                @SuppressWarnings("unchecked")
                long s = Json.integer((Map<String, Object>) o, "s", -1);
                last = Math.max(last, s);
            }
        }
        final long ack = last;
        ui.access(new Runnable() {
            @Override
            public void run() {
                try {
                    ui.handleEvents(events);
                } catch (IllegalStateException e) {
                    gone();
                    return;
                }
                send("{\"t\":\"ack\",\"ev\":" + ack + "}");
            }
        });
    }

    private void gone() {
        httpSession.removeTransport(ui.getId(), this);
        send("{\"t\":\"gone\"}");
        close(CLOSE_GONE, "gone");
    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
        close(StatusCode.BAD_DATA, "binary frames are not used");
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        closed = true;
        boolean wasCurrent = httpSession.removeTransport(ui.getId(), this);
        if (wasCurrent && ui.getState() != UI.State.CLOSED) {
            ui.access(new Runnable() {
                @Override
                public void run() {
                    ui.disconnect(WebSocketTransport.this); // deltas accumulate for a reconnect within the grace period
                }
            });
        }
        LOG.log(Level.FINE, "WebSocket of {0} closed: {1} {2}", new Object[] {ui, statusCode, reason});
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        LOG.log(Level.FINE, "WebSocket error for " + ui, cause);
    }
}
