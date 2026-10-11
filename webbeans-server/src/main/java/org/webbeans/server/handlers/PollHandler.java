package org.webbeans.server.handlers;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.webbeans.core.json.Json;
import org.webbeans.server.HttpSession;
import org.webbeans.server.PollTransport;
import org.webbeans.server.ServerConfig;
import org.webbeans.server.ServerTransport;
import org.webbeans.server.http.WbRequest;
import org.webbeans.ui.UI;

/**
 * {@code POST /wb/poll}: the long-poll transport, the fallback for clients that cannot open a WebSocket
 * ({@link WebSocketHandler}). Every request carries {@code uiId} and {@code t}:
 * <ul>
 * <li>{@code hello} ({@code lastSeq}): binds a new transport to the UI; the reply carries the {@code init} or the
 * replayed deltas.</li>
 * <li>{@code ev} ({@code events}): dispatches client events; the reply carries the deltas they produced.</li>
 * <li>{@code poll}: waits up to the configured time for server-initiated messages (timers, pushes).</li>
 * <li>{@code bye}: the tab is closing; its UI is disposed.</li>
 * </ul>
 * Replies are {@code {"messages":[...]}}; 401 means not logged in, 410 that the UI or session no longer exists
 * (the client reloads to the login page), 409 that the client must say hello first.
 */
@SuppressWarnings("serial") // servlets are nominally Serializable; these are never serialised
public final class PollHandler extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(PollHandler.class.getName());

    private final ServerConfig config;
    private final AuthHandler auth;

    public PollHandler(ServerConfig config, AuthHandler auth) {
        this.config = config;
        this.auth = auth;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        WbRequest r = new WbRequest(req, resp, config);
        try {
            route(r);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Poll request failed", e);
            if (!r.isResponded()) {
                r.sendJson(500, "{\"error\":\"internal\"}");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!r.isResponded()) {
                r.sendJson(503, "{\"error\":\"shutting down\"}");
            }
        } finally {
            if (!r.isResponded()) {
                r.sendJson(400, "{\"error\":\"bad request\"}");
            }
        }
    }

    private void route(WbRequest r) throws IOException, InterruptedException {
        if (!r.getMethod().equals("POST")) {
            r.sendText(405, "Method not allowed");
            return;
        }
        HttpSession s = auth.currentSession(r);
        if (s == null) {
            r.sendJson(401, "{\"error\":\"not authenticated\"}");
            return;
        }
        String token = r.getHeader("X-WB-Token");
        if (token == null) {
            token = r.getQueryParameter("token");
        }
        if (!s.getCsrfToken().equals(token)) {
            r.sendJson(403, "{\"error\":\"bad token\"}");
            return;
        }
        Map<String, Object> body = Json.parseObject(r.getBodyText());
        String uiId = Json.string(body, "uiId");
        String t = Json.string(body, "t");
        final UI ui = uiId == null ? null : s.getUI(uiId);
        if (ui == null || ui.getState() == UI.State.CLOSED) {
            r.sendJson(410, "{\"error\":\"gone\"}");
            return;
        }
        if ("hello".equals(t)) {
            hello(r, s, ui, Json.integer(body, "lastSeq", -1));
        } else if ("ev".equals(t)) {
            events(r, s, ui, Json.array(body, "events"));
        } else if ("poll".equals(t)) {
            poll(r, s, ui);
        } else if ("bye".equals(t)) {
            s.replaceTransport(ui.getId(), null);
            ui.close();
            r.sendEmpty(204);
        }
    }

    private void hello(WbRequest r, HttpSession s, final UI ui, final long lastSeq) throws IOException, InterruptedException {
        final PollTransport transport = new PollTransport();
        ServerTransport old = s.replaceTransport(ui.getId(), transport);
        if (old != null) {
            old.close();
        }
        Future<?> f = ui.access(new Runnable() {
            @Override
            public void run() {
                ui.connect(transport, lastSeq);
            }
        });
        if (!await(f)) {
            s.replaceTransport(ui.getId(), null);
            r.sendJson(410, "{\"error\":\"gone\"}");
            return;
        }
        respond(r, transport.drain());
    }

    private void events(WbRequest r, HttpSession s, final UI ui, final List<Object> events) throws IOException, InterruptedException {
        PollTransport transport = pollTransport(s, ui);
        if (transport == null || transport.isClosed()) {
            r.sendJson(409, "{\"error\":\"hello first\"}");
            return;
        }
        if (events != null && !events.isEmpty()) {
            Future<?> f = ui.access(new Runnable() {
                @Override
                public void run() {
                    ui.handleEvents(events);
                }
            });
            if (!await(f)) {
                r.sendJson(410, "{\"error\":\"gone\"}");
                return;
            }
        }
        respond(r, transport.drain());
    }

    private void poll(WbRequest r, HttpSession s, UI ui) throws IOException, InterruptedException {
        PollTransport transport = pollTransport(s, ui);
        if (transport == null) {
            r.sendJson(409, "{\"error\":\"hello first\"}");
            return;
        }
        List<String> msgs = transport.poll(config.getPollWait().toMillis(), TimeUnit.MILLISECONDS);
        if (msgs.isEmpty() && transport.isClosed()) {
            if (ui.getState() == UI.State.CLOSED || !s.isValid()) {
                r.sendJson(410, "{\"error\":\"gone\"}");
            } else {
                r.sendJson(409, "{\"error\":\"superseded\"}"); // a newer hello replaced this transport
            }
            return;
        }
        respond(r, msgs);
    }

    /** The tab's transport when it is a long-poll one (a WebSocket may have taken over). */
    private static PollTransport pollTransport(HttpSession s, UI ui) {
        ServerTransport t = s.getTransport(ui.getId());
        return t instanceof PollTransport ? (PollTransport) t : null;
    }

    /** Waits for a session task; false when the session/UI is closed. */
    private static boolean await(Future<?> f) throws InterruptedException {
        try {
            f.get(60, TimeUnit.SECONDS);
            return true;
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof IllegalStateException) {
                return false;
            }
            LOG.log(Level.SEVERE, "Session task failed", c);
            return true;
        } catch (TimeoutException e) {
            LOG.log(Level.WARNING, "Session task did not finish within 60s; replying with what is queued");
            return true;
        }
    }

    private static void respond(WbRequest r, List<String> messages) throws IOException {
        StringBuilder sb = new StringBuilder(64 + messages.size() * 256);
        sb.append("{\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(messages.get(i));
        }
        sb.append("]}");
        r.sendJson(200, sb.toString());
    }
}
