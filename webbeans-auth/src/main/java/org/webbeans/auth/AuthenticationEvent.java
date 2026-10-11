package org.webbeans.auth;

import java.time.Instant;

/** Passed to {@code AuthenticationListener}s. */
public final class AuthenticationEvent {

    public enum Type { LOGIN_SUCCEEDED, LOGIN_FAILED, LOGGED_OUT }

    public enum LogoutReason { USER, TIMEOUT, MAX_LIFETIME, REVALIDATION_FAILED, ADMIN, CONCURRENT, SHUTDOWN }

    private final Type type;
    private final Principal principal;
    private final String attemptedUser;
    private final String providerId;
    private final String remoteAddress;
    private final String message;
    private final LogoutReason logoutReason;
    private final Instant time = Instant.now();

    public AuthenticationEvent(Type type, Principal principal, String attemptedUser, String providerId, String remoteAddress,
            String message, LogoutReason logoutReason) {
        this.type = type;
        this.principal = principal;
        this.attemptedUser = attemptedUser;
        this.providerId = providerId;
        this.remoteAddress = remoteAddress;
        this.message = message;
        this.logoutReason = logoutReason;
    }

    public Type getType() {
        return type;
    }

    /** Null for failures. */
    public Principal getPrincipal() {
        return principal;
    }

    /** The user name attempted, if the credentials carried one. */
    public String getAttemptedUser() {
        return attemptedUser;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public String getMessage() {
        return message;
    }

    public LogoutReason getLogoutReason() {
        return logoutReason;
    }

    public Instant getTime() {
        return time;
    }

    @Override
    public String toString() {
        return type + " " + (principal != null ? principal.getId() : attemptedUser) + " via " + providerId + " from " + remoteAddress
                + (message != null ? " (" + message + ")" : "");
    }
}
