package org.webbeans.server;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.JavaUtilLog;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.webbeans.auth.Principal;
import org.webbeans.auth.Security;
import org.webbeans.core.registry.Registry;
import org.webbeans.server.handlers.AppHandler;
import org.webbeans.server.handlers.AuthHandler;
import org.webbeans.server.handlers.PollHandler;
import org.webbeans.server.handlers.UploadHandler;
import org.webbeans.server.handlers.WebSocketHandler;
import org.webbeans.ui.UISession;

/**
 * The HTTP front end: an embedded Jetty 9.4 server with one servlet context at the configured context path.
 * <ul>
 * <li>{@code /wb/ws} — the WebSocket transport ({@link WebSocketHandler}), the normal way a tab talks to its UI;</li>
 * <li>{@code /wb/poll} — the long-poll transport ({@link PollHandler}), the fallback when no WebSocket can be opened;</li>
 * <li>{@code /wb/upload} — file uploads ({@link UploadHandler}); downloads are {@code /wb/download/<token>} in the app servlet;</li>
 * <li>{@code /login}, {@code /logout}, {@code /auth/*} — authentication ({@link AuthHandler});</li>
 * <li>everything else — the page, the client bundle, resources, blobs and module pages ({@link AppHandler}).</li>
 * </ul>
 * The {@link org.webbeans.ui.Transport} abstraction keeps the UI runtime independent of the transport in use.
 */
public final class WbServer {

    private static final Logger LOG = Logger.getLogger(WbServer.class.getName());

    static {
        // Jetty logs through java.util.logging like the rest of the platform (unless the application chose otherwise)
        if (System.getProperty("org.eclipse.jetty.util.log.class") == null) {
            Log.setLog(new JavaUtilLog());
        }
    }

    private final ServerConfig config;
    private final SessionManager sessions;
    private final AppHandler app;
    private final AuthHandler auth;
    private final PollHandler poll;
    private final WebSocketHandler ws;
    private final UploadHandler upload;
    private Server server;
    private final ChangeListener registryListener = new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
            app.invalidateBundle();
        }
    };

    public WbServer(ServerConfig config) {
        this.config = config;
        this.sessions = new SessionManager(config);
        this.auth = new AuthHandler(config, sessions);
        this.app = new AppHandler(config, auth, new Bundler());
        this.poll = new PollHandler(config, auth);
        this.ws = new WebSocketHandler(config, auth);
        this.upload = new UploadHandler(config, auth);
    }

    public ServerConfig getConfig() {
        return config;
    }

    public SessionManager getSessions() {
        return sessions;
    }

    /** The port actually bound (useful with port 0 in tests). */
    public int getPort() {
        if (server == null) {
            return config.getPort();
        }
        return ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    }

    /** The embedded Jetty server, for applications that add their own handlers or connectors before {@link #start}. */
    public synchronized Server getJetty() {
        if (server == null) {
            server = build();
        }
        return server;
    }

    private Server build() {
        QueuedThreadPool pool = new QueuedThreadPool(Math.max(8, config.getHttpThreads()), 4);
        pool.setName("wb-http");
        pool.setDaemon(true);
        Server s = new Server(pool);
        HttpConfiguration http = new HttpConfiguration();
        http.setSendServerVersion(false);
        http.setSendDateHeader(true);
        ServerConnector connector = new ServerConnector(s, new HttpConnectionFactory(http));
        connector.setHost(config.getHost());
        connector.setPort(config.getPort());
        connector.setIdleTimeout(Math.max(60000L, config.getPollWait().toMillis() * 2 + 10000L));
        s.addConnector(connector);

        String cp = config.getContextPath();
        ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        ctx.setContextPath(cp.isEmpty() ? "/" : cp);
        ctx.addServlet(new ServletHolder("wb-poll", poll), "/wb/poll");
        ctx.addServlet(new ServletHolder("wb-upload", upload), "/wb/upload");
        if (config.isWebSocketEnabled()) {
            ctx.addServlet(new ServletHolder("wb-ws", ws), "/wb/ws");
        }
        ServletHolder authHolder = new ServletHolder("wb-auth", auth);
        ctx.addServlet(authHolder, "/login");
        ctx.addServlet(authHolder, "/logout");
        ctx.addServlet(authHolder, "/auth/*");
        ctx.addServlet(new ServletHolder("wb-app", app), "/");
        s.setHandler(ctx);
        s.setStopAtShutdown(false);
        s.setStopTimeout(1000);
        return s;
    }

    public synchronized void start() throws IOException {
        if (server != null && server.isRunning()) {
            return;
        }
        Security.setPrincipalSource(new Security.PrincipalSource() {
            @Override
            public Principal current() {
                UISession s = UISession.current();
                return s == null ? null : s.getPrincipal();
            }
        });
        Registry.getDefault().addChangeListener(registryListener);
        Server s = getJetty();
        try {
            s.start();
        } catch (Exception e) {
            server = null;
            Registry.getDefault().removeChangeListener(registryListener);
            throw e instanceof IOException ? (IOException) e : new IOException("Cannot start Jetty: " + e, e);
        }
        LOG.log(Level.INFO, "WebBeans listening on http://{0}:{1}{2}/ (Jetty {3}, WebSocket {4})", new Object[] {
            config.getHost(), String.valueOf(getPort()), config.getContextPath(), Server.getVersion(),
            config.isWebSocketEnabled() ? "on" : "off"});
    }

    public synchronized void stop() {
        if (server == null) {
            return;
        }
        Registry.getDefault().removeChangeListener(registryListener);
        sessions.shutdown();
        try {
            server.stop();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error stopping Jetty", e);
        }
        server = null;
        LOG.log(Level.INFO, "WebBeans server stopped");
    }
}
