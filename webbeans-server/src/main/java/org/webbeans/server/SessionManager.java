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
        return s;
    }

    /** The valid session with that id, or null. Does not touch it. */
    public HttpSession get(String id) {
        if (id == null) {
            return null;
        }
        HttpSession s = sessions.get(id);
        if (s == null) {
            return null;
        }
        if (!s.isValid() || s.getAuthentication().isExpired()) {
            invalidate(s, AuthenticationEvent.LogoutReason.MAX_LIFETIME);
            return null;
        }
        return s;
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
