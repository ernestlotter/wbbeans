package org.webbeans.auth.http;

import java.io.IOException;

/** Container-independent response for {@code AuthHttpHandler}s. */
public interface AuthHttpResponse {

    void setHeader(String name, String value);

    void send(int status, String contentType, String body) throws IOException;

    void redirect(String location) throws IOException;

    /** Creates the session for the authentication and sets the cookie; returns the URL to send the browser to. */
    String establishSession(org.webbeans.auth.Authentication authentication) throws IOException;
}
