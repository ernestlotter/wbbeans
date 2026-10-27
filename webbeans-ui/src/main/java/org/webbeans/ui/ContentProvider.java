package org.webbeans.ui;

import java.io.IOException;
import java.util.Map;

/**
 * A module-provided binary endpoint under {@link #getPrefix()} ({@code /wb/tiles/} for map tiles, say), served
 * by the HTTP front end to signed-in users only. Register implementations with
 * {@code @ServiceProvider(service = ContentProvider.class)}. Unlike {@link PageProvider}, {@link #get} runs on
 * the HTTP thread, not the session executor: it may block on I/O but must be thread-safe and must not touch
 * components.
 */
public interface ContentProvider {

    /** The path prefix handled, e.g. {@code /wb/tiles/}. */
    String getPrefix();

    /**
     * @param session the user's session (for roles or per-user data; do not run UI code on this thread)
     * @param path    the request path after the prefix (URL-decoded)
     * @param query   the query parameters
     * @return the content, or null for "not found"
     */
    Content get(UISession session, String path, Map<String, String> query) throws IOException;

    /** Bytes with a content type and an optional {@code Cache-Control} value. */
    final class Content {
        private final byte[] bytes;
        private final String contentType;
        private final String cacheControl;

        public Content(byte[] bytes, String contentType, String cacheControl) {
            this.bytes = bytes;
            this.contentType = contentType;
            this.cacheControl = cacheControl;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public String getContentType() {
            return contentType;
        }

        public String getCacheControl() {
            return cacheControl;
        }
    }
}
