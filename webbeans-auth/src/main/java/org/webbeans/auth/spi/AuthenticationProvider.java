package org.webbeans.auth.spi;

import org.webbeans.auth.AuthContext;
import org.webbeans.auth.Authentication;
import org.webbeans.auth.AuthenticationException;
import org.webbeans.auth.AuthenticationResult;
import org.webbeans.auth.Credentials;
import org.webbeans.auth.LoginMethod;

/**
 * One authentication mechanism. Register with
 * {@code @ServiceProvider(service = AuthenticationProvider.class, position = N)}; the {@code Authenticator}
 * asks providers in position order and the first that {@link #supports} the credential class and does not
 * answer {@code notMine()} decides.
 */
public interface AuthenticationProvider {

    /** Short stable id used in URLs ({@code /auth/<id>/...}), configuration and {@code Principal.getProviderId()}. */
    String getId();

    /** Human name; {@code #KEY} allowed. */
    String getDisplayName();

    boolean supports(Class<? extends Credentials> type);

    AuthenticationResult authenticate(Credentials credentials, AuthContext context) throws AuthenticationException;

    /** How the login page offers this provider; null = not offered (programmatic or infrastructure providers). */
    default LoginMethod getLoginMethod() {
        return null;
    }

    /** Called periodically per {@code SessionPolicy.revalidateInterval}; false ends the session. */
    default boolean revalidate(Authentication authentication, AuthContext context) {
        return true;
    }

    /** Logout hook (token revocation, end-session). Must not throw. */
    default void logout(Authentication authentication, AuthContext context) {
    }
}
