package org.webbeans.auth.spi;

import org.webbeans.auth.AuthenticationEvent;

/** Audit / integration hook. Register with {@code @ServiceProvider(service = AuthenticationListener.class)}. */
public interface AuthenticationListener {

    default void loginSucceeded(AuthenticationEvent event) {
    }

    default void loginFailed(AuthenticationEvent event) {
    }

    default void loggedOut(AuthenticationEvent event) {
    }
}
