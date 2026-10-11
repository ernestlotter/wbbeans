package org.webbeans.auth.spi;

import org.webbeans.auth.http.AuthHttpRequest;
import org.webbeans.auth.http.AuthHttpResponse;

/** Extra HTTP endpoints a provider needs (device login, magic links), mounted under {@code /auth/<prefix>/}. */
public interface AuthHttpHandler {

    /** Path prefix below {@code /auth/}, e.g. {@code "magic"} serves {@code /auth/magic/...}. */
    String getPathPrefix();

    void handle(AuthHttpRequest request, AuthHttpResponse response) throws java.io.IOException;
}
