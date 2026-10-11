package org.webbeans.auth;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** How and when a principal was authenticated. Kept by the session; {@link #getSecrets()} never leaves the server. */
public final class Authentication {

    private final Principal principal;
    private final Instant time;
    private final Instant expiresAt;
    private final Set<String> methods;
    private final Map<String, Object> secrets;

    public Authentication(Principal principal, Instant time, Instant expiresAt, Set<String> methods, Map<String, Object> secrets) {
        this.principal = principal;
        this.time = time;
        this.expiresAt = expiresAt;
        this.methods = Collections.unmodifiableSet(new LinkedHashSet<String>(methods));
        this.secrets = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(secrets));
    }

    public Principal getPrincipal() {
        return principal;
    }

    public Instant getTime() {
        return time;
    }

    /** Null = valid until the session ends. */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /** "password", "oidc", "token", "header", "mfa:totp"... for step-up decisions. */
    public Set<String> getMethods() {
        return methods;
    }

    /** Server-side only (access/refresh tokens for calling the provider's APIs). */
    public Map<String, Object> getSecrets() {
        return secrets;
    }

    @Override
    public String toString() {
        return "Authentication[" + principal + " at " + time + " via " + methods + "]";
    }
}
