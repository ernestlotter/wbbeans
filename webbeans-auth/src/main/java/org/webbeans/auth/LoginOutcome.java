package org.webbeans.auth;

/** The result of {@code Authenticator.login}: an authentication to build a session from, a challenge to show, or a failure. */
public final class LoginOutcome {

    public enum Kind { AUTHENTICATED, CHALLENGE, FAILURE }

    private final Kind kind;
    private final Authentication authentication;
    private final Challenge challenge;
    private final String message;

    private LoginOutcome(Kind kind, Authentication authentication, Challenge challenge, String message) {
        this.kind = kind;
        this.authentication = authentication;
        this.challenge = challenge;
        this.message = message;
    }

    public static LoginOutcome authenticated(Authentication a) {
        return new LoginOutcome(Kind.AUTHENTICATED, a, null, null);
    }

    public static LoginOutcome challenge(Challenge c) {
        return new LoginOutcome(Kind.CHALLENGE, null, c, null);
    }

    public static LoginOutcome failure(String messageKey) {
        return new LoginOutcome(Kind.FAILURE, null, null, messageKey);
    }

    public Kind getKind() {
        return kind;
    }

    public Authentication getAuthentication() {
        return authentication;
    }

    public Challenge getChallenge() {
        return challenge;
    }

    /** A message key ({@code #ERR_Login}) or literal text. */
    public String getMessage() {
        return message;
    }
}
