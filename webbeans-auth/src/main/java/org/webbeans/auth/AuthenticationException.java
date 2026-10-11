package org.webbeans.auth;

/** An infrastructure failure while authenticating (directory down, key fetch failed) — not a wrong password. */
public class AuthenticationException extends Exception {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
