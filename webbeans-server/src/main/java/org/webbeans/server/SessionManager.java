package org.webbeans.server;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.Lookup;
import org.webbeans.auth.Authentication;
import org.webbeans.auth.AuthenticationEvent;
import org.webbeans.auth.Authenticator;
import org.webbeans.auth.Principal;
import org.webbeans.auth.SimpleAuthContext;
import org.webbeans.auth.spi.SessionPolicy;
import org.webbeans.auth.spi.SessionStore;
import org.webbeans.core.files.DataDir;
import org.webbeans.core.files.UserFiles;
import org.webbeans.ui.UI;
import org.webbeans.ui.UISession;

/**
 * Creates, finds and expires {@link HttpSession}s. A background sweeper enforces the idle timeout and maximum
 * lifetime (from the {@link SessionPolicy} in the lookup, else the {@link ServerConfig}), revalidates
 * authentications with their provider, and closes UIs whose tab has been silent for longer than the grace period.
 */
public final class SessionManager {

    private static final Logger LOG = Logger.getLogger(SessionManager.class.getName());
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ServerConfig config;
    private final Map<String, HttpSession> sessions = new ConcurrentHashMap<String, HttpSession>();
    private final ExecutorService backend;
    private final ScheduledExecutorService sweeper;
    /** Last activity stamp written to the store per session, to touch it only when something happened. */
    private final Map<String, Instant> stored = new ConcurrentHashMap<String, Instant>();

    public SessionManager(ServerConfig config) {
        ServerSessionDirectory.register(this);
        this.config = config;
        this.backend = Executors.newFixedThreadPool(config.getBackendThreads(), daemon("wb-session"));
        this.sweeper = Executors.newSingleThreadScheduledExecutor(daemon("wb-sweeper"));
        sweeper.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    sweep();
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Session sweep failed", e);
                }
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    private static ThreadFactory daemon(final String prefix) {
        return new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
    }

    public static String randomToken() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** Creates a session for a successful login. Closes other sessions of the user if the policy forbids them. */
    public HttpSession create(Authentication authentication, Locale locale, String remoteAddress) throws IOException {
        Principal p = authentication.getPrincipal();
        SessionPolicy policy = policy();
        if (policy != null && !policy.allowConcurrentSessions(p)) {
            for (HttpSession s : new ArrayList<HttpSession>(sessions.values())) {
                if (s.getPrincipal().equals(p)) {
                    invalidate(s, AuthenticationEvent.LogoutReason.CONCURRENT);
                }
            }
        }
        UserFiles files = null;
        DataDir dd = Lookup.getDefault().lookup(DataDir.class);
        if (dd != null) {
            files = dd.userFiles(p.getName());
        }
        UISession ui = new UISession(authentication, locale, files, backend);
        ui.setAttribute(org.webbeans.ui.SessionDirectory.REMOTE_ADDRESS, remoteAddress);
        HttpSession s = new HttpSession(randomToken(), randomToken(), ui, remoteAddress);
        sessions.put(s.getId(), s);
        LOG.log(Level.INFO, "Session {0} created for {1} from {2}", new Object[] {s.getId().substring(0, 6), p.getName(), remoteAddress});
        SessionStore store = store();
        if (store != null) {
            try {
                store.put(toStored(s, policy));
                stored.put(s.getId(), s.getLastActivity());
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Session store put failed: " + e);
            }
        }
        return s;
    }

    /** The valid session with that id — adopting it from the {@link SessionStore} if another instance created it — or null. */
    public HttpSession get(String id) {
        if (id == null) {
            return null;
        }
        HttpSession s = sessions.get(id);
        if (s == null) {
            s = adopt(id);
            if (s == null) {
                return null;
            }
        }
        if (!s.isValid() || s.getAuthentication().isExpired()) {
            invalidate(s, AuthenticationEvent.LogoutReason.MAX_LIFETIME);
            return null;
        }
        return s;
    }

    /** A session another instance (or this one before a restart) created: same id and CSRF token, a fresh UI session. */
    private HttpSession adopt(String id) {
        SessionStore store = store();
        if (store == null || id.length() < 8 || !id.matches("[A-Za-z0-9_-]+")) {
            return null;
        }
        SessionStore.StoredSession st;
        try {
            st = store.get(id);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Session store get failed: " + e);
            return null;
        }
        if (st == null || st.isExpired() || st.getAuthentication().isExpired()) {
            return null;
        }
        synchronized (this) {
            HttpSession existing = sessions.get(id);
            if (existing != null) {
                return existing;
            }
            Principal p = st.getAuthentication().getPrincipal();
            UserFiles files = null;
            DataDir dd = Lookup.getDefault().lookup(DataDir.class);
            if (dd != null) {
                try {
                    files = dd.userFiles(p.getName());
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Cannot open the user files of " + p.getName() + ": " + e);
                }
            }
            UISession ui = new UISession(st.getAuthentication(), st.getLocale() != null ? st.getLocale() : Locale.getDefault(), files, backend);
            ui.setAttribute(org.webbeans.ui.SessionDirectory.REMOTE_ADDRESS, st.getRemoteAddress());
            HttpSession s = new HttpSession(st.getId(), st.getCsrfToken(), ui, st.getRemoteAddress());
            sessions.put(id, s);
            stored.put(id, s.getLastActivity());
            LOG.log(Level.INFO, "Session {0} of {1} adopted from the session store (created on {2})",
                    new Object[] {id.substring(0, 6), p.getName(), st.getNodeId()});
            return s;
        }
    }

    private SessionStore store() {
        SessionStore s = config.getSessionStore();
        return s != null ? s : Lookup.getDefault().lookup(SessionStore.class);
    }

    private SessionStore.StoredSession toStored(HttpSession s, SessionPolicy policy) {
        return new SessionStore.StoredSession(s.getId(), s.getCsrfToken(), s.getAuthentication(), s.getLocale(), s.getRemoteAddress(),
                config.getNodeId(), s.getCreated(), s.getLastActivity(), expiry(s, policy));
    }

    private Instant expiry(HttpSession s, SessionPolicy policy) {
        Principal p = s.getPrincipal();
        Instant idle = s.getLastActivity().plus(idleTimeout(p, policy));
        Instant max = s.getCreated().plus(maxLifetime(p, policy));
        Instant e = idle.isBefore(max) ? idle : max;
        Instant auth = s.getAuthentication().getExpiresAt();
        return auth != null && auth.isBefore(e) ? auth : e;
    }

    public int size() {
        return sessions.size();
    }

    public List<HttpSession> getSessions() {
        return new ArrayList<HttpSession>(sessions.values());
    }

    /** Ends the session: the authenticator's logout hook, then all UIs. Idempotent. */
    public void invalidate(HttpSession s, AuthenticationEvent.LogoutReason reason) {
        if (sessions.remove(s.getId()) == null) {
            return;
        }
        s.markInvalid();
        stored.remove(s.getId());
        SessionStore store = store();
        if (store != null && reason != AuthenticationEvent.LogoutReason.SHUTDOWN) {   // a stopping node leaves its sessions for the others
            try {
                store.remove(s.getId());
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Session store remove failed: " + e);
            }
        }
        Authenticator auth = Authenticator.getDefault();
        if (auth != null) {
            try {
                auth.logout(s.getAuthentication(), new SimpleAuthContext(s.getRemoteAddress(), null, s.getLocale()), reason);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Logout hook failed", e);
            }
        }
        for (ServerTransport t : s.getTransports().values()) {
            t.close();
        }
        s.getUISession().close();
        LOG.log(Level.INFO, "Session {0} of {1} ended: {2}", new Object[] {s.getId().substring(0, 6), s.getPrincipal().getName(), reason});
    }

    private SessionPolicy policy() {
        return Lookup.getDefault().lookup(SessionPolicy.class);
    }

    private Duration idleTimeout(Principal p, SessionPolicy policy) {
        Duration d = policy != null ? policy.idleTimeout(p) : null;
        return d != null ? d : config.getSessionIdleTimeout();
    }

    private Duration maxLifetime(Principal p, SessionPolicy policy) {
        Duration d = policy != null ? policy.maxLifetime(p) : null;
        return d != null ? d : config.getSessionMaxLifetime();
    }

    void sweep() {
        Instant now = Instant.now();
        SessionPolicy policy = policy();
        Authenticator auth = Authenticator.getDefault();
        for (HttpSession s : new ArrayList<HttpSession>(sessions.values())) {
            Principal p = s.getPrincipal();
            if (!s.isValid()) {
                invalidate(s, AuthenticationEvent.LogoutReason.ADMIN);
                continue;
            }
            if (s.getAuthentication().isExpired()
                    || s.getCreated().plus(maxLifetime(p, policy)).isBefore(now)) {
                invalidate(s, AuthenticationEvent.LogoutReason.MAX_LIFETIME);
                continue;
            }
            if (s.getLastActivity().plus(idleTimeout(p, policy)).isBefore(now)) {
                invalidate(s, AuthenticationEvent.LogoutReason.TIMEOUT);
                continue;
            }
            Duration reval = policy != null ? policy.revalidateInterval(p) : null;
            if (reval != null && auth != null && s.getLastRevalidated().plus(reval).isBefore(now)) {
                boolean ok = auth.revalidate(s.getAuthentication(), new SimpleAuthContext(s.getRemoteAddress(), null, s.getLocale()));
                if (!ok) {
                    invalidate(s, AuthenticationEvent.LogoutReason.REVALIDATION_FAILED);
                    continue;
                }
                s.setLastRevalidated(now);
            }
            sweepUIs(s, now);
            touchStore(s, policy);
        }
    }

    /** Pushes the activity stamp to the store when it moved since the last push (every sweep, so ≤ one write per 10 s per session). */
    private void touchStore(HttpSession s, SessionPolicy policy) {
        SessionStore store = store();
        if (store == null) {
            return;
        }
        Instant last = stored.get(s.getId());
        Instant now = s.getLastActivity();
        if (last != null && !now.isAfter(last)) {
            return;
        }
        try {
            store.touch(s.getId(), now, expiry(s, policy));
            stored.put(s.getId(), now);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Session store touch failed: " + e);
        }
    }

    private void sweepUIs(HttpSession s, Instant now) {
        long grace = config.getUiGracePeriod().toMillis();
        long pollSlack = config.getPollWait().toMillis() * 2 + 5000;
        for (final UI ui : s.getUISession().getUIs()) {
            final ServerTransport t = s.getTransport(ui.getId());
            boolean stale;
            if (ui.getState() == UI.State.INIT) {
                // page requested but the client never said hello
                stale = ui.getLastActivity().plusMillis(grace + pollSlack).isBefore(now);
            } else if (ui.getState() == UI.State.DETACHED) {
                stale = ui.getDetachedAt() != null && ui.getDetachedAt().plusMillis(grace).isBefore(now);
            } else if (ui.getState() == UI.State.ATTACHED) {
                stale = t == null || t.idleMillis() > grace + pollSlack;
            } else {
                stale = false;
            }
            if (stale) {
                LOG.log(Level.INFO, "Closing abandoned UI {0}", ui);
                s.replaceTransport(ui.getId(), null);
                ui.close();
                if (t != null) {
                    t.close();
                }
            }
        }
    }

    public void shutdown() {
        sweeper.shutdownNow();
        for (HttpSession s : new ArrayList<HttpSession>(sessions.values())) {
            invalidate(s, AuthenticationEvent.LogoutReason.SHUTDOWN);
        }
        backend.shutdown();
        try {
            backend.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
