package org.webbeans.auth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Identity asserted by infrastructure (a reverse proxy doing SSO) through request headers. */
public final class HeaderCredentials implements Credentials {

    private final Map<String, String> headers;
    private final String remoteAddress;
    private final boolean fromTrustedProxy;

    public HeaderCredentials(Map<String, String> headers, String remoteAddress, boolean fromTrustedProxy) {
        Map<String, String> lower = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            lower.put(e.getKey().toLowerCase(java.util.Locale.ROOT), e.getValue());
        }
        this.headers = Collections.unmodifiableMap(lower);
        this.remoteAddress = remoteAddress;
        this.fromTrustedProxy = fromTrustedProxy;
    }

    /** Header names are lower-cased. */
    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getHeader(String name) {
        return headers.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public boolean isFromTrustedProxy() {
        return fromTrustedProxy;
    }
}
