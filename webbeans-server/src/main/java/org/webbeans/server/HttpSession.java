package org.webbeans.server;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.webbeans.auth.Authentication;
import org.webbeans.auth.Principal;
import org.webbeans.ui.UI;
import org.webbeans.ui.UISession;

/**
 * One authenticated browser (cookie) session. Owns the {@link UISession} with all the tabs of that browser and the
 * transports (WebSocket or long-poll) of the tabs that are currently connected.
 */
public final class HttpSession {

    private final String id;
    private final String csrfToken;
    private final UISession uiSession;
    private final String remoteAddress;
    private final Instant created = Instant.now();
    private volatile Instant lastRevalidated = Instant.now();
    private volatile boolean invalid;
    private final Map<String, ServerTransport> transports = new ConcurrentHashMap<String, ServerTransport>();

    HttpSession(String id, String csrfToken, UISession uiSession, String remoteAddress) {
        this.id = id;
        this.csrfToken = csrfToken;
        this.uiSession = uiSession;
        this.remoteAddress = remoteAddress;
    }

    public String getId() {
        return id;
    }

    /** Per-session token the client sends back as {@code X-WB-Token} and in forms as {@code csrf}. */
    public String getCsrfToken() {
        return csrfToken;
    }

    public UISession getUISession() {
        return uiSession;
    }

    public Authentication getAuthentication() {
        return uiSession.getAuthentication();
    }

    public Principal getPrincipal() {
        return uiSession.getPrincipal();
    }

    public Locale getLocale() {
        return uiSession.getLocale();
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public Instant getCreated() {
        return created;
    }

    public Instant getLastActivity() {
        return uiSession.getLastActivity();
    }

    Instant getLastRevalidated() {
        return lastRevalidated;
    }

    void setLastRevalidated(Instant t) {
        lastRevalidated = t;
    }

    public boolean isValid() {
        return !invalid && uiSession.getState() == UISession.State.ACTIVE;
    }

    void markInvalid() {
        invalid = true;
    }

    public void touch() {
        uiSession.touch();
    }

    /** The transport currently bound to the tab, or null. */
    public ServerTransport getTransport(String uiId) {
        return transports.get(uiId);
    }

    /** Binds (or with null unbinds) the tab's transport; returns the previous one, which the caller closes. */
    public ServerTransport replaceTransport(String uiId, ServerTransport t) {
        return t == null ? transports.remove(uiId) : transports.put(uiId, t);
    }

    /** Unbinds the transport only if it is still the tab's current one (a reconnect may have replaced it). */
    public boolean removeTransport(String uiId, ServerTransport t) {
        return transports.remove(uiId, t);
    }

    Map<String, ServerTransport> getTransports() {
        return transports;
    }

    public UI getUI(String uiId) {
        return uiSession.getUI(uiId);
    }
}
