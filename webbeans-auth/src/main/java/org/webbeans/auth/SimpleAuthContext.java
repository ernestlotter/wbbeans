package org.webbeans.auth;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** A plain {@link AuthContext}; the server builds one per request, tests build them directly. */
public final class SimpleAuthContext implements AuthContext {

    private final String remoteAddress;
    private final String userAgent;
    private final Locale locale;
    private final Map<String, String> headers = new HashMap<String, String>();
    private final Map<String, String> parameters = new HashMap<String, String>();
    private final Map<String, Object> flowState = new LinkedHashMap<String, Object>();
    private Authentication existing;

    public SimpleAuthContext(String remoteAddress, String userAgent, Locale locale) {
        this.remoteAddress = remoteAddress;
        this.userAgent = userAgent;
        this.locale = locale == null ? Locale.getDefault() : locale;
    }

    public SimpleAuthContext header(String name, String value) {
        headers.put(name.toLowerCase(Locale.ROOT), value);
        return this;
    }

    public SimpleAuthContext parameter(String name, String value) {
        parameters.put(name, value);
        return this;
    }

    public SimpleAuthContext existing(Authentication a) {
        this.existing = a;
        return this;
    }

    @Override
    public String getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public Locale getLocale() {
        return locale;
    }

    @Override
    public String getHeader(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
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
