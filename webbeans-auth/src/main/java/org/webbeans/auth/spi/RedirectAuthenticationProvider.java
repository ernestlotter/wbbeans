package org.webbeans.auth.spi;

import java.net.URI;

import org.webbeans.auth.AuthContext;

/**
 * A provider whose flow sends the browser away and receives it back at {@code /auth/<id>/callback} with
 * {@code CallbackCredentials}. The platform wires both endpoints and keeps the flow state across the round trip.
 */
public interface RedirectAuthenticationProvider extends AuthenticationProvider {

    /** Builds the URL to redirect to; store anything needed in the callback in {@code context.getFlowState()}. */
    URI start(AuthContext context, URI callbackUri);
}
