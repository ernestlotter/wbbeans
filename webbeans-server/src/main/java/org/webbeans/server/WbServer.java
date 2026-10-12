package org.webbeans.server;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.SecuredRedirectHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.JavaUtilLog;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.ssl.SslContextFactory;
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
 * <p>
 * TLS: with {@link ServerConfig#https} the server terminates HTTPS itself (an {@code https} connector next to, or
 * instead of, the plain one; {@link ServerConfig#httpsRedirect} sends plain requests over to it; HTTPS responses
 * carry HSTS). Without it, a TLS-terminating proxy in front works with {@code --trusted-proxy}/{@code --secure-cookies}.
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

    /** The plain HTTP port actually bound (useful with port 0 in tests); -1 when HTTP is disabled. */
    public int getPort() {
        if (server == null) {
            return config.isHttpEnabled() ? config.getPort() : -1;
        }
        ServerConnector c = connector("http");
        return c == null ? -1 : c.getLocalPort();
    }

    /** The HTTPS port actually bound; -1 when the server does not terminate TLS. */
    public int getHttpsPort() {
        if (server == null) {
            return config.isHttpsEnabled() ? config.getHttpsPort() : -1;
        }
        ServerConnector c = connector("https");
        return c == null ? -1 : c.getLocalPort();
    }

    private ServerConnector connector(String name) {
        for (Connector c : server.getConnectors()) {
            if (c instanceof ServerConnector && name.equals(c.getName())) {
                return (ServerConnector) c;
            }
        }
        return null;
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
        long idle = Math.max(60000L, config.getPollWait().toMillis() * 2 + 10000L);
        boolean tls = config.isHttpsEnabled();
        if (tls) {
            http.setSecureScheme("https");
            http.setSecurePort(config.getHttpsPort());   // where SecuredRedirectHandler sends plain requests
        }
        if (config.isHttpEnabled()) {
            ServerConnector connector = new ServerConnector(s, new HttpConnectionFactory(http));
            connector.setName("http");
            connector.setHost(config.getHost());
            connector.setPort(config.getPort());
            connector.setIdleTimeout(idle);
            s.addConnector(connector);
        }
        if (tls) {
            SslContextFactory.Server ssl = new SslContextFactory.Server();
            ssl.setKeyStorePath(config.getKeyStore().toAbsolutePath().toString());
            ssl.setKeyStoreType(config.getKeyStoreType());
            if (config.getKeyStorePassword() != null) {
                ssl.setKeyStorePassword(config.getKeyStorePassword());
            }
            if (config.getKeyManagerPassword() != null) {
                ssl.setKeyManagerPassword(config.getKeyManagerPassword());
            }
            HttpConfiguration https = new HttpConfiguration(http);
            // no SNI host check: a certificate that does not match the requested name is for the browser to judge
            https.addCustomizer(new SecureRequestCustomizer(false));
            ServerConnector connector = new ServerConnector(s,
                    new SslConnectionFactory(ssl, HttpVersion.HTTP_1_1.asString()), new HttpConnectionFactory(https));
            connector.setName("https");
            connector.setHost(config.getHost());
            connector.setPort(config.getHttpsPort());
            connector.setIdleTimeout(idle);
            s.addConnector(connector);
        }
        if (!config.isHttpEnabled() && !tls) {
            throw new IllegalStateException("Neither HTTP nor HTTPS is enabled");
        }

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
        if (tls && config.isHttpsRedirect() && config.isHttpEnabled()) {
            HandlerList handlers = new HandlerList();
            handlers.addHandler(new SecuredRedirectHandler());   // plain requests → https://host:securePort/...
            handlers.addHandler(ctx);
            s.setHandler(handlers);
        } else {
            s.setHandler(ctx);
        }
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
        StringBuilder urls = new StringBuilder();
        if (getPort() >= 0) {
            urls.append("http://").append(config.getHost()).append(':').append(getPort()).append(config.getContextPath()).append('/');
            if (config.isHttpsRedirect() && getHttpsPort() >= 0) {
                urls.append(" (redirecting to HTTPS)");
            }
        }
        if (getHttpsPort() >= 0) {
            urls.append(urls.length() == 0 ? "" : " and ").append("https://").append(config.getHost()).append(':')
                .append(getHttpsPort()).append(config.getContextPath()).append('/');
        }
        LOG.log(Level.INFO, "WebBeans listening on {0} (Jetty {1}, WebSocket {2})", new Object[] {
            urls, Server.getVersion(), config.isWebSocketEnabled() ? "on" : "off"});
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
