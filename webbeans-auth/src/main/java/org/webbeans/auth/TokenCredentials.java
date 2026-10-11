package org.webbeans.auth;

/** A bearer token the client obtained elsewhere (Firebase ID token, a JWT from an SSO, an API key). */
public final class TokenCredentials implements Credentials {

    private final String scheme;
    private final String token;

    public TokenCredentials(String scheme, String token) {
        this.scheme = scheme;
        this.token = token;
    }

    /** "firebase", "jwt", "apikey"... matched against {@code TokenAuthenticationProvider.getScheme()}. */
    public String getScheme() {
        return scheme;
    }

    public String getToken() {
        return token;
    }

    @Override
    public String toString() {
        return "TokenCredentials[" + scheme + "]";
    }
}
