package org.webbeans.auth.http;

import java.util.Map;

/** Container-independent view of a request, implemented by the server for {@code AuthHttpHandler}s. */
public interface AuthHttpRequest {

    String getMethod();

    /** Path below {@code /auth/<prefix>/}. */
    String getPath();

    String getHeader(String name);

    Map<String, String> getParameters();

    String getRemoteAddress();

    /** The request body (form or JSON) as text, or empty. */
    String getBody();
}
