package org.webbeans.server.handlers;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.webbeans.server.HttpSession;
import org.webbeans.server.ServerConfig;
import org.webbeans.server.WebSocketTransport;
import org.webbeans.server.http.WbRequest;
import org.webbeans.ui.UI;

/**
 * {@code GET /wb/ws?ui=<uiId>&token=<csrf>}: the WebSocket transport. The upgrade request is authenticated like
 * any other request (session cookie) and additionally carries the session's CSRF token, so a page on another origin
 * cannot open a socket with the browser's cookies alone. One socket per tab; a successful upgrade yields a
 * {@link WebSocketTransport}, which takes over from the moment the client says {@code hello}.
 * Refusals: 401 (no session), 403 (bad token), 410 (unknown or closed UI).
 */
@SuppressWarnings("serial") // servlets are nominally Serializable; these are never serialised
public final class WebSocketHandler extends WebSocketServlet {

    private static final Logger LOG = Logger.getLogger(WebSocketHandler.class.getName());

    private final ServerConfig config;
    private final AuthHandler auth;

    public WebSocketHandler(ServerConfig config, AuthHandler auth) {
        this.config = config;
        this.auth = auth;
    }

    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.getPolicy().setIdleTimeout(config.getWebSocketIdleTimeout().toMillis());
        factory.getPolicy().setMaxTextMessageSize(config.getMaxPollBody());
        factory.getPolicy().setMaxBinaryMessageSize(1024);
        factory.setCreator(new WebSocketCreator() {
            @Override
            public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
                try {
                    return create(req, resp);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "WebSocket upgrade failed", e);
                    return null;
                }
            }
        });
    }

    private Object create(ServletUpgradeRequest req, ServletUpgradeResponse resp) throws IOException {
        WbRequest r = new WbRequest(req.getHttpServletRequest(), null, config);
        HttpSession s = auth.currentSession(r);
        if (s == null) {
            resp.sendError(401, "Not authenticated");
            return null;
        }
        String token = r.getQueryParameter("token");
        if (token == null || !s.getCsrfToken().equals(token)) {
            resp.sendError(403, "Bad token");
            return null;
        }
        String uiId = r.getQueryParameter("ui");
        UI ui = uiId == null ? null : s.getUI(uiId);
        if (ui == null || ui.getState() == UI.State.CLOSED) {
            resp.sendError(410, "Gone");
            return null;
        }
        s.touch();
        return new WebSocketTransport(s, ui);
    }
}
