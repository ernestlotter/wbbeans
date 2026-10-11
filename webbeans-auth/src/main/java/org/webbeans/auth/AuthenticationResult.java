package org.webbeans.auth;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** What a provider answers. Build with the static factories; {@code success} results take extra details fluently. */
public final class AuthenticationResult {

    public enum Kind { SUCCESS, FAILURE, CHALLENGE, NOT_MINE }

    private final Kind kind;
    private final Principal principal;
    private final Challenge challenge;
    private final String message;
    private Instant expiresAt;
    private final Set<String> methods = new LinkedHashSet<String>();
    private final Map<String, Object> secrets = new LinkedHashMap<String, Object>();

    private AuthenticationResult(Kind kind, Principal principal, Challenge challenge, String message) {
        this.kind = kind;
        this.principal = principal;
        this.challenge = challenge;
        this.message = message;
    }

    public static AuthenticationResult success(Principal principal) {
        if (principal == null) {
            throw new NullPointerException("principal");
        }
        return new AuthenticationResult(Kind.SUCCESS, principal, null, null);
    }

    /** @param messageKey a generic, user-facing message such as {@code #ERR_Login}; never reveal which part was wrong */
    public static AuthenticationResult failure(String messageKey) {
        return new AuthenticationResult(Kind.FAILURE, null, null, messageKey);
    }

    public static AuthenticationResult challenge(Challenge challenge) {
        return new AuthenticationResult(Kind.CHALLENGE, null, challenge, null);
    }

    /** The provider cannot judge these credentials (unknown realm, wrong scheme); the next provider is asked. */
    public static AuthenticationResult notMine() {
        return new AuthenticationResult(Kind.NOT_MINE, null, null, null);
    }

    public AuthenticationResult expiresAt(Instant when) {
        this.expiresAt = when;
        return this;
    }

    public AuthenticationResult method(String method) {
        methods.add(method);
        return this;
    }

    /** Server-side secret kept in {@link Authentication#getSecrets()}. */
    public AuthenticationResult secret(String name, Object value) {
        secrets.put(name, value);
        return this;
    }

    public Kind getKind() {
        return kind;
    }

    public Principal getPrincipal() {
        return principal;
    }

    public Challenge getChallenge() {
        return challenge;
    }

    public String getMessage() {
        return message;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Set<String> getMethods() {
        return methods;
    }

    public Map<String, Object> getSecrets() {
        return secrets;
    }

    public Authentication toAuthentication(Principal mapped) {
        return new Authentication(mapped, Instant.now(), expiresAt, methods, secrets);
    }
}
