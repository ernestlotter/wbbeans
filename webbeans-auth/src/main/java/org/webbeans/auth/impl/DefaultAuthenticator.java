package org.webbeans.auth.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import org.webbeans.auth.AuthContext;
import org.webbeans.auth.Authentication;
import org.webbeans.auth.AuthenticationEvent;
import org.webbeans.auth.AuthenticationException;
import org.webbeans.auth.AuthenticationResult;
import org.webbeans.auth.Authenticator;
import org.webbeans.auth.Challenge;
import org.webbeans.auth.ChallengeResponse;
import org.webbeans.auth.Credentials;
import org.webbeans.auth.LoginOutcome;
import org.webbeans.auth.PasswordCredentials;
import org.webbeans.auth.Principal;
import org.webbeans.auth.spi.AuthenticationListener;
import org.webbeans.auth.spi.AuthenticationProvider;
import org.webbeans.auth.spi.LoginThrottle;
import org.webbeans.auth.spi.RoleMapper;

/** The standard {@link Authenticator}: providers, role mappers, throttle and listeners from {@code Lookup.getDefault()}. */
@ServiceProvider(service = Authenticator.class)
public final class DefaultAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(DefaultAuthenticator.class.getName());
    public static final String ERR_LOGIN = "#ERR_Login";
    public static final String ERR_THROTTLED = "#ERR_Throttled";
    public static final String ERR_UNAVAILABLE = "#ERR_Unavailable";
    public static final String ERR_CHALLENGE_EXPIRED = "#ERR_ChallengeExpired";

    /** A challenge waiting for its answer. */
    private static final class Pending {
        final Challenge challenge;
        final AuthenticationProvider provider;
        final Map<String, Object> flowState;
        final Authentication existing;

        Pending(Challenge challenge, AuthenticationProvider provider, Map<String, Object> flowState, Authentication existing) {
            this.challenge = challenge;
            this.provider = provider;
            this.flowState = new LinkedHashMap<String, Object>(flowState);
            this.existing = existing;
        }
    }

    private final Map<String, Pending> pending = Collections.synchronizedMap(new LinkedHashMap<String, Pending>());
    private final LoginThrottle fallbackThrottle = new DefaultLoginThrottle(10, Duration.ofMinutes(5));

    @Override
    public List<AuthenticationProvider> getProviders() {
        return new ArrayList<AuthenticationProvider>(Lookup.getDefault().lookupAll(AuthenticationProvider.class));
    }

    @Override
    public AuthenticationProvider getProvider(String id) {
        for (AuthenticationProvider p : getProviders()) {
            if (p.getId().equals(id)) {
                return p;
            }
        }
        return null;
    }

    @Override
    public List<AuthenticationProvider> getLoginProviders() {
        List<AuthenticationProvider> out = new ArrayList<AuthenticationProvider>();
        for (AuthenticationProvider p : getProviders()) {
            if (p.getLoginMethod() != null) {
                out.add(p);
            }
        }
        return out;
    }

    private LoginThrottle throttle() {
        LoginThrottle t = Lookup.getDefault().lookup(LoginThrottle.class);
        return t != null ? t : fallbackThrottle;
    }

    @Override
    public LoginOutcome login(Credentials credentials, AuthContext context) {
        return run(credentials, context, null);
    }

    @Override
    public LoginOutcome reauthenticate(Authentication existing, Credentials credentials, AuthContext context) {
        return run(credentials, context, existing);
    }

    private LoginOutcome run(Credentials credentials, AuthContext context, Authentication existing) {
        String attempted = credentials instanceof PasswordCredentials ? ((PasswordCredentials) credentials).getUsername() : null;
        String ipKey = "ip:" + context.getRemoteAddress();
        String userKey = attempted != null ? "user:" + attempted.toLowerCase(java.util.Locale.ROOT) : null;
        LoginThrottle throttle = throttle();
        if (throttle.isBlocked(ipKey) || (userKey != null && throttle.isBlocked(userKey))) {
            fire(AuthenticationEvent.Type.LOGIN_FAILED, null, attempted, null, context, "throttled");
            return LoginOutcome.failure(ERR_THROTTLED);
        }
        boolean anySupported = false;
        try {
            for (AuthenticationProvider p : getProviders()) {
                if (!p.supports(credentials.getClass())) {
                    continue;
                }
                anySupported = true;
                AuthenticationResult r;
                try {
                    r = p.authenticate(credentials, context);
                } catch (AuthenticationException e) {
                    LOG.log(Level.WARNING, "Provider " + p.getId() + " failed", e);
                    fire(AuthenticationEvent.Type.LOGIN_FAILED, null, attempted, p.getId(), context, "unavailable: " + e.getMessage());
                    return LoginOutcome.failure(ERR_UNAVAILABLE);
                } catch (RuntimeException e) {
                    LOG.log(Level.SEVERE, "Provider " + p.getId() + " threw", e);
                    fire(AuthenticationEvent.Type.LOGIN_FAILED, null, attempted, p.getId(), context, "error: " + e);
                    return LoginOutcome.failure(ERR_UNAVAILABLE);
                }
                if (r == null || r.getKind() == AuthenticationResult.Kind.NOT_MINE) {
                    continue;
                }
                return outcome(r, p, context, existing, attempted, ipKey, userKey, throttle);
            }
        } finally {
            if (credentials instanceof PasswordCredentials) {
                ((PasswordCredentials) credentials).clear();
            }
        }
        if (!anySupported) {
            LOG.log(Level.WARNING, "No authentication provider accepts {0}", credentials.getClass().getName());
        }
        recordFailure(throttle, ipKey, userKey);
        fire(AuthenticationEvent.Type.LOGIN_FAILED, null, attempted, null, context, anySupported ? "rejected by all providers" : "no provider");
        return LoginOutcome.failure(ERR_LOGIN);
    }

    private LoginOutcome outcome(AuthenticationResult r, AuthenticationProvider p, AuthContext context, Authentication existing,
            String attempted, String ipKey, String userKey, LoginThrottle throttle) {
        switch (r.getKind()) {
            case SUCCESS: {
                Principal principal = r.getPrincipal().withProvider(p.getId());
                principal = mapRoles(principal, p);
                Authentication auth = r.toAuthentication(principal);
                throttle.recordSuccess(ipKey);
                if (userKey != null) {
                    throttle.recordSuccess(userKey);
                }
                fire(AuthenticationEvent.Type.LOGIN_SUCCEEDED, principal, attempted, p.getId(), context, null);
                return LoginOutcome.authenticated(auth);
            }
            case CHALLENGE: {
                Challenge c = r.getChallenge();
                expirePending();
                pending.put(c.getId(), new Pending(c, p, context.getFlowState(), existing));
                return LoginOutcome.challenge(c);
            }
            case FAILURE:
            default:
                recordFailure(throttle, ipKey, userKey);
                fire(AuthenticationEvent.Type.LOGIN_FAILED, null, attempted, p.getId(), context, r.getMessage());
                return LoginOutcome.failure(r.getMessage() != null ? r.getMessage() : ERR_LOGIN);
        }
    }

    @Override
    public LoginOutcome answer(ChallengeResponse response, AuthContext context) {
        expirePending();
        Pending pd = pending.remove(response.getChallengeId());
        if (pd == null) {
            return LoginOutcome.failure(ERR_CHALLENGE_EXPIRED);
        }
        context.getFlowState().putAll(pd.flowState);
        AuthenticationResult r;
        try {
            r = pd.provider.authenticate(response, context);
        } catch (AuthenticationException e) {
            LOG.log(Level.WARNING, "Provider " + pd.provider.getId() + " failed", e);
            return LoginOutcome.failure(ERR_UNAVAILABLE);
        }
        if (r == null || r.getKind() == AuthenticationResult.Kind.NOT_MINE) {
            return LoginOutcome.failure(ERR_LOGIN);
        }
        String ipKey = "ip:" + context.getRemoteAddress();
        return outcome(r, pd.provider, context, pd.existing, null, ipKey, null, throttle());
    }

    private void expirePending() {
        synchronized (pending) {
            Iterator<Pending> it = pending.values().iterator();
            while (it.hasNext()) {
                if (it.next().challenge.isExpired()) {
                    it.remove();
                }
            }
        }
    }

    private static void recordFailure(LoginThrottle throttle, String ipKey, String userKey) {
        throttle.recordFailure(ipKey);
        if (userKey != null) {
            throttle.recordFailure(userKey);
        }
    }

    private Principal mapRoles(Principal principal, AuthenticationProvider provider) {
        Principal current = principal;
        for (RoleMapper m : Lookup.getDefault().lookupAll(RoleMapper.class)) {
            Set<String> roles = m.mapRoles(current, provider);
            current = current.withRoles(roles == null ? new HashSet<String>() : roles);
        }
        return current;
    }

    @Override
    public void logout(Authentication authentication, AuthContext context, AuthenticationEvent.LogoutReason reason) {
        if (authentication == null) {
            return;
        }
        AuthenticationProvider p = getProvider(authentication.getPrincipal().getProviderId());
        if (p != null) {
            try {
                p.logout(authentication, context);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "logout hook of " + p.getId() + " failed", e);
            }
        }
        AuthenticationEvent ev = new AuthenticationEvent(AuthenticationEvent.Type.LOGGED_OUT, authentication.getPrincipal(), null,
                authentication.getPrincipal().getProviderId(), context != null ? context.getRemoteAddress() : null, null, reason);
        for (AuthenticationListener l : Lookup.getDefault().lookupAll(AuthenticationListener.class)) {
            try {
                l.loggedOut(ev);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "listener failed", e);
            }
        }
    }

    @Override
    public boolean revalidate(Authentication authentication, AuthContext context) {
        if (authentication.isExpired()) {
            return false;
        }
        AuthenticationProvider p = getProvider(authentication.getPrincipal().getProviderId());
        if (p == null) {
            return true;
        }
        try {
            return p.revalidate(authentication, context);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "revalidate of " + p.getId() + " failed", e);
            return false;
        }
    }

    private void fire(AuthenticationEvent.Type type, Principal principal, String attempted, String providerId, AuthContext context, String message) {
        AuthenticationEvent ev = new AuthenticationEvent(type, principal, attempted, providerId, context.getRemoteAddress(), message, null);
        if (type == AuthenticationEvent.Type.LOGIN_SUCCEEDED) {
            LOG.log(Level.INFO, "Login: {0}", ev);
        } else {
            LOG.log(Level.INFO, "Login failed: {0}", ev);
        }
        for (AuthenticationListener l : Lookup.getDefault().lookupAll(AuthenticationListener.class)) {
            try {
                if (type == AuthenticationEvent.Type.LOGIN_SUCCEEDED) {
                    l.loginSucceeded(ev);
                } else {
                    l.loginFailed(ev);
                }
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "listener failed", e);
            }
        }
    }

    /** For tests: the time a pending challenge was registered is not exposed; this clears all. */
    void clearPending() {
        pending.clear();
    }

    static Instant now() {
        return Instant.now();
    }
}
