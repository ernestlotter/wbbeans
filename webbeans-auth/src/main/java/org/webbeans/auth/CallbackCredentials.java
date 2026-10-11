package org.webbeans.auth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The return leg of a redirect flow (OAuth2/OIDC): the provider id, the callback parameters and the flow state. */
public final class CallbackCredentials implements Credentials {

    private final String providerId;
    private final Map<String, String> parameters;
    private final String state;
    private final String redirectUri;

    public CallbackCredentials(String providerId, Map<String, String> parameters, String state, String redirectUri) {
        this.providerId = providerId;
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<String, String>(parameters));
        this.state = state;
        this.redirectUri = redirectUri;
    }

    public String getProviderId() {
        return providerId;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public String getState() {
        return state;
    }

    public String getRedirectUri() {
        return redirectUri;
    }
}
