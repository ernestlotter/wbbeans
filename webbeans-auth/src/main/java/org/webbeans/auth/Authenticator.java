package org.webbeans.auth;

import java.util.List;

import org.openide.util.Lookup;
import org.webbeans.auth.spi.AuthenticationProvider;

/**
 * The platform-side entry point: runs the providers, role mappers, throttle and listeners. The server's
 * handlers call it; so may custom endpoints. Sessions themselves are created by the caller from the
 * {@link Authentication} in the outcome (the server module owns sessions).
 */
public interface Authenticator {

    static Authenticator getDefault() {
        return Lookup.getDefault().lookup(Authenticator.class);
    }

    LoginOutcome login(Credentials credentials, AuthContext context);

    /** Completes a challenge started by an earlier {@link #login}. */
    LoginOutcome answer(ChallengeResponse response, AuthContext context);

    /** Re-authenticates (step-up) or refreshes an existing authentication; the session is kept by the caller. */
    LoginOutcome reauthenticate(Authentication existing, Credentials credentials, AuthContext context);

    /** Runs the provider's logout hook and notifies listeners. */
    void logout(Authentication authentication, AuthContext context, AuthenticationEvent.LogoutReason reason);

    /** True unless the provider says the authentication is no longer valid. */
    boolean revalidate(Authentication authentication, AuthContext context);

    /** Providers in position order. */
    List<AuthenticationProvider> getProviders();

    AuthenticationProvider getProvider(String id);

    /** Providers offering a login method, for the login page. */
    List<AuthenticationProvider> getLoginProviders();
}
