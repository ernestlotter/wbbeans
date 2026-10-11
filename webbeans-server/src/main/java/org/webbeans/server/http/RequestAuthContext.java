package org.webbeans.server.http;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.webbeans.auth.AuthContext;
import org.webbeans.auth.Authentication;

/** {@link AuthContext} over a {@link WbRequest}; the flow state map is owned by the caller (see the flow store). */
public final class RequestAuthContext implements AuthContext {

    private final WbRequest request;
    private final Map<String, Object> flowState;
    private final Authentication existing;
    private final Map<String, String> parameters;

    public RequestAuthContext(WbRequest request, Map<String, Object> flowState, Authentication existing) {
        this.request = request;
        this.flowState = flowState != null ? flowState : new LinkedHashMap<String, Object>();
        this.existing = existing;
        Map<String, String> p;
        try {
            p = request.getParameters();
        } catch (IOException e) {
            p = Collections.emptyMap();
        }
        this.parameters = p;
    }

    @Override
    public String getRemoteAddress() {
        return request.getRemoteAddress();
    }

    @Override
    public String getUserAgent() {
        return request.getUserAgent();
    }

    @Override
    public Locale getLocale() {
        return request.getLocale();
    }

    @Override
    public String getHeader(String name) {
        return request.getHeader(name);
    }

    @Override
    public String getParameter(String name) {
        return parameters.get(name);
    }

    @Override
    public Map<String, Object> getFlowState() {
        return flowState;
    }

    @Override
    public Authentication getExistingAuthentication() {
        return existing;
    }
}
