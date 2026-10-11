package org.webbeans.auth;

import java.util.Locale;
import java.util.Map;

/** What a provider may know about the request it is authenticating. Implemented by the server. */
public interface AuthContext {

    String getRemoteAddress();

    String getUserAgent();

    Locale getLocale();

    /** A request header, or null. */
    String getHeader(String name);

    /** A request parameter (query or form), or null. */
    String getParameter(String name);

    /** Mutable state that survives a redirect round trip (OAuth state, PKCE verifier) or a challenge. */
    Map<String, Object> getFlowState();

    /** The authentication of an existing session when re-authenticating or refreshing; else null. */
    Authentication getExistingAuthentication();
}
