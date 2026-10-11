package org.webbeans.ui;

import java.util.Map;

/**
 * A module-provided full HTML page outside the single-page application, served by the HTTP front end under
 * {@link #getPrefix()} to signed-in users only (the help module serves {@code /wb/help/<topic>}). Register
 * implementations with {@code @ServiceProvider(service = PageProvider.class)}; {@link #render} runs on the
 * session executor, so the registry and the services of the user's session are available.
 */
public interface PageProvider {

    /** The path prefix handled, e.g. {@code /wb/help/}. */
    String getPrefix();

    /**
     * @param session  the user's session
     * @param basePath the application's context path ({@code ""} or {@code /app}) to prefix absolute links with
     * @param path     the request path after the prefix (URL-decoded)
     * @param query    the query parameters
     * @return a complete HTML document, or null for "not found"
     */
    String render(UISession session, String basePath, String path, Map<String, String> query);
}
