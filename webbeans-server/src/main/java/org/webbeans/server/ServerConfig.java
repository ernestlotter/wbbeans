package org.webbeans.server;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Settings of the HTTP front end. Everything has a usable default; the launcher maps command line options onto it. */
public final class ServerConfig {

    private String host = "0.0.0.0";
    private int port = 8080;
    private String contextPath = "";
    private final java.util.List<String> imageSources = new java.util.ArrayList<String>();
    private String applicationTitle = "WebBeans";
    private boolean devMode;
    private boolean secureCookies;
    private Duration sessionIdleTimeout = Duration.ofMinutes(30);
    private Duration sessionMaxLifetime = Duration.ofHours(12);
    private Duration uiGracePeriod = Duration.ofMinutes(2);
    private Duration pollWait = Duration.ofSeconds(25);
    private int maxPollBody = 1024 * 1024;
    private long maxUploadSize = 256L * 1024 * 1024;
    private boolean webSocketEnabled = true;
    private Duration webSocketIdleTimeout = Duration.ofMinutes(5);
    private int httpThreads = Math.max(8, Runtime.getRuntime().availableProcessors() * 4);
    private int backendThreads = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
    private final List<String> trustedProxies = new ArrayList<String>();
    private boolean httpEnabled = true;
    private int httpsPort = -1;
    private Path keyStore;
    private String keyStorePassword;
    private String keyManagerPassword;
    private String keyStoreType;
    private boolean httpsRedirect;
    private boolean hsts = true;

    public String getHost() {
        return host;
    }

    public ServerConfig host(String host) {
        this.host = host;
        return this;
    }

    public int getPort() {
        return port;
    }

    public ServerConfig port(int port) {
        this.port = port;
        return this;
    }

    /** Prefix under which the application is served, {@code ""} for the root; no trailing slash. */
    public String getContextPath() {
        return contextPath;
    }

    /**
     * Extra origins the browser may load images from ({@code img-src} of the Content Security Policy), e.g.
     * {@code "https://*.tile.openstreetmap.org"} for a map's direct tile source. Default: the server itself only,
     * which is why map tiles are proxied through {@code /wb/tiles/} unless an origin is listed here.
     */
    public ServerConfig imageSources(String... origins) {
        for (String o : origins) {
            if (o != null && !o.trim().isEmpty() && !imageSources.contains(o.trim())) {
                imageSources.add(o.trim());
            }
        }
        return this;
    }

    public java.util.List<String> getImageSources() {
        return java.util.Collections.unmodifiableList(imageSources);
    }

    public ServerConfig contextPath(String cp) {
        if (cp == null || cp.equals("/")) {
            cp = "";
        }
        if (!cp.isEmpty() && !cp.startsWith("/")) {
            cp = "/" + cp;
        }
        if (cp.endsWith("/")) {
            cp = cp.substring(0, cp.length() - 1);
        }
        this.contextPath = cp;
        return this;
    }

    public String getApplicationTitle() {
        return applicationTitle;
    }

    public ServerConfig applicationTitle(String t) {
        this.applicationTitle = t;
        return this;
    }

    /** Rebuild the client bundle on every page load and send no cache headers: for renderer development. */
    public boolean isDevMode() {
        return devMode;
    }

    public ServerConfig devMode(boolean b) {
        this.devMode = b;
        return this;
    }

    /** Mark cookies {@code Secure}; set when the server is reached through HTTPS (a TLS proxy in front). */
    public boolean isSecureCookies() {
        return secureCookies;
    }

    public ServerConfig secureCookies(boolean b) {
        this.secureCookies = b;
        return this;
    }

    public Duration getSessionIdleTimeout() {
        return sessionIdleTimeout;
    }

    public ServerConfig sessionIdleTimeout(Duration d) {
        this.sessionIdleTimeout = d;
        return this;
    }

    public Duration getSessionMaxLifetime() {
        return sessionMaxLifetime;
    }

    public ServerConfig sessionMaxLifetime(Duration d) {
        this.sessionMaxLifetime = d;
        return this;
    }

    /** How long a UI whose client stopped polling is kept for a reconnect before it is closed. */
    public Duration getUiGracePeriod() {
        return uiGracePeriod;
    }

    public ServerConfig uiGracePeriod(Duration d) {
        this.uiGracePeriod = d;
        return this;
    }

    /** Longest a {@code poll} request waits for a message before answering empty. */
    public Duration getPollWait() {
        return pollWait;
    }

    public ServerConfig pollWait(Duration d) {
        this.pollWait = d;
        return this;
    }

    /** Largest accepted upstream message (poll body or WebSocket text frame). */
    public int getMaxPollBody() {
        return maxPollBody;
    }

    public ServerConfig maxPollBody(int bytes) {
        this.maxPollBody = bytes;
        return this;
    }

    /** Largest file accepted by {@code /wb/upload} (an {@code Upload} component may set a smaller limit). */
    public long getMaxUploadSize() {
        return maxUploadSize;
    }

    public ServerConfig maxUploadSize(long bytes) {
        this.maxUploadSize = bytes;
        return this;
    }

    /** Whether the page offers the WebSocket transport ({@code /wb/ws}); long polling is always available. */
    public boolean isWebSocketEnabled() {
        return webSocketEnabled;
    }

    public ServerConfig webSocketEnabled(boolean enabled) {
        this.webSocketEnabled = enabled;
        return this;
    }

    /** A WebSocket silent for this long (no event, no keep-alive) is closed; the client reconnects. */
    public Duration getWebSocketIdleTimeout() {
        return webSocketIdleTimeout;
    }

    public ServerConfig webSocketIdleTimeout(Duration d) {
        this.webSocketIdleTimeout = d;
        return this;
    }

    /** Size of Jetty's thread pool (connections, long polls, WebSocket callbacks). */
    public int getHttpThreads() {
        return httpThreads;
    }

    public ServerConfig httpThreads(int n) {
        this.httpThreads = n;
        return this;
    }

    /** Size of the thread pool the session executors run on. */
    public int getBackendThreads() {
        return backendThreads;
    }

    public ServerConfig backendThreads(int n) {
        this.backendThreads = n;
        return this;
    }

    /** Addresses whose {@code X-Forwarded-For} / identity headers are believed. */
    public List<String> getTrustedProxies() {
        return Collections.unmodifiableList(trustedProxies);
    }

    public ServerConfig trustedProxy(String address) {
        trustedProxies.add(address);
        return this;
    }

    // ---- TLS ---------------------------------------------------------------------------------------------------

    /** Whether the plain HTTP connector ({@link #getPort()}) is opened; off for HTTPS-only servers. */
    public boolean isHttpEnabled() {
        return httpEnabled;
    }

    public ServerConfig httpEnabled(boolean b) {
        this.httpEnabled = b;
        return this;
    }

    /** The HTTPS port (0 = a free one), or -1 when the server does not terminate TLS itself (a proxy may still, see {@link #isSecureCookies()}). */
    public int getHttpsPort() {
        return httpsPort;
    }

    public boolean isHttpsEnabled() {
        return httpsPort >= 0 && keyStore != null;
    }

    /**
     * Serves HTTPS on {@code port} with the server certificate from {@code keyStore} (PKCS12 or JKS; the type is
     * taken from the file name unless {@link #keyStoreType} is set). Port 0 binds a free port.
     */
    public ServerConfig https(int port, Path keyStore, String keyStorePassword) {
        this.httpsPort = port;
        this.keyStore = keyStore;
        this.keyStorePassword = keyStorePassword;
        return this;
    }

    public Path getKeyStore() {
        return keyStore;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    /** The key's own password when it differs from the store's. */
    public String getKeyManagerPassword() {
        return keyManagerPassword;
    }

    public ServerConfig keyManagerPassword(String pw) {
        this.keyManagerPassword = pw;
        return this;
    }

    /** {@code PKCS12} or {@code JKS}; derived from the key store's extension when unset ({@code .p12}/{@code .pfx} → PKCS12). */
    public String getKeyStoreType() {
        if (keyStoreType != null) {
            return keyStoreType;
        }
        String name = keyStore == null ? "" : keyStore.getFileName().toString().toLowerCase();
        return name.endsWith(".p12") || name.endsWith(".pfx") ? "PKCS12" : "JKS";
    }

    public ServerConfig keyStoreType(String type) {
        this.keyStoreType = type;
        return this;
    }

    /** Redirect plain HTTP requests to the HTTPS port (keeps the HTTP port open for the redirect only). */
    public boolean isHttpsRedirect() {
        return httpsRedirect;
    }

    public ServerConfig httpsRedirect(boolean b) {
        this.httpsRedirect = b;
        return this;
    }

    /** Send {@code Strict-Transport-Security} on HTTPS responses (one year). */
    public boolean isHsts() {
        return hsts;
    }

    public ServerConfig hsts(boolean b) {
        this.hsts = b;
        return this;
    }
}
