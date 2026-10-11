package org.webbeans.auth.spi;

/** A provider verifying tokens the browser obtained with a vendor SDK ({@code TokenCredentials} posted to {@code /auth/token}). */
public interface TokenAuthenticationProvider extends AuthenticationProvider {

    /** Matched against {@code TokenCredentials.getScheme()}. */
    String getScheme();
}
