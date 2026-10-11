package org.webbeans.auth;

/** Thrown by {@link Security#checkRole} and friends. */
public class AccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AccessDeniedException(String message) {
        super(message);
    }
}
