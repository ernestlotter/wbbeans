package org.webbeans.server.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.webbeans.server.ServerConfig;

/**
 * Convenience wrapper around one servlet request/response pair: the path below the context, parsed parameters and
 * cookies, and response helpers that set the security headers. Also used for the HTTP part of a WebSocket upgrade.
 */
public final class WbRequest {

    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final ServerConfig config;
    private final String path;
    private Map<String, String> query;
    private Map<String, String> form;
    private Map<String, String> cookies;
    private byte[] body;
    private boolean responded;

    public WbRequest(HttpServletRequest request, HttpServletResponse response, ServerConfig config) {
        this.request = request;
        this.response = response;
        this.config = config;
        // servlet path + path info: decoded, and already relative to the context path
        String sp = request.getServletPath();
        String pi = request.getPathInfo();
        String p = (sp == null ? "" : sp) + (pi == null ? "" : pi);
        if (p.isEmpty()) {
            p = request.getRequestURI();
            String cp = config.getContextPath();
            if (!cp.isEmpty() && p.startsWith(cp)) {
                p = p.substring(cp.length());
            }
        }
        this.path = p.isEmpty() ? "/" : p;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    /** The response, or null for the request side of a WebSocket upgrade. */
    public HttpServletResponse getResponse() {
        return response;
    }

    public ServerConfig getConfig() {
        return config;
    }

    public String getMethod() {
        return request.getMethod();
    }

    /** The raw query string, or null. */
    public String getRawQuery() {
        return request.getQueryString();
    }

    /** Path below the context path, always starting with {@code /}. */
    public String getPath() {
        return path;
    }

    public String getContextPath() {
        return config.getContextPath();
    }

    public String getHeader(String name) {
        return request.getHeader(name);
    }

    public String getUserAgent() {
        return getHeader("User-Agent");
    }

    /** TLS terminated here, or by a proxy that says so ({@code X-Forwarded-Proto}), or {@code --secure-cookies}. */
    public boolean isHttps() {
        if (request.isSecure()) {
            return true;
        }
        String proto = getHeader("X-Forwarded-Proto");
        return config.isSecureCookies() || "https".equalsIgnoreCase(proto);
    }

    /** Client address; the first {@code X-Forwarded-For} hop when the peer is a trusted proxy. */
    public String getRemoteAddress() {
        String peer = request.getRemoteAddr();
        if (peer == null) {
            peer = "?";
        }
        if (config.getTrustedProxies().contains(peer)) {
            String fwd = getHeader("X-Forwarded-For");
            if (fwd != null && !fwd.trim().isEmpty()) {
                return fwd.split(",")[0].trim();
            }
        }
        return peer;
    }

    public Map<String, String> getQuery() {
        if (query == null) {
            query = parseForm(request.getQueryString());
        }
        return query;
    }

    public String getQueryParameter(String name) {
        return getQuery().get(name);
    }

    public byte[] getBody() throws IOException {
        if (body == null) {
            InputStream in = request.getInputStream();
            try {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                int total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > config.getMaxPollBody()) {
                        throw new IOException("Request body too large");
                    }
                    out.write(buf, 0, n);
                }
                body = out.toByteArray();
            } finally {
                in.close();
            }
        }
        return body;
    }

    public String getBodyText() throws IOException {
        return new String(getBody(), StandardCharsets.UTF_8);
    }

    /** Form fields of an {@code application/x-www-form-urlencoded} body, else empty. */
    public Map<String, String> getForm() throws IOException {
        if (form == null) {
            String ct = getHeader("Content-Type");
            if (ct != null && ct.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) {
                form = parseForm(getBodyText());
            } else {
                form = new LinkedHashMap<String, String>();
            }
        }
        return form;
    }

    /** Query parameter, else form field, else null. */
    public String getParameter(String name) throws IOException {
        String v = getQuery().get(name);
        return v != null ? v : getForm().get(name);
    }

    /** Query and form fields merged, query winning. */
    public Map<String, String> getParameters() throws IOException {
        Map<String, String> all = new LinkedHashMap<String, String>(getForm());
        all.putAll(getQuery());
        return all;
    }

    public Map<String, String> getCookies() {
        if (cookies == null) {
            cookies = new LinkedHashMap<String, String>();
            Enumeration<String> lines = request.getHeaders("Cookie");
            while (lines != null && lines.hasMoreElements()) {
                for (String part : lines.nextElement().split(";")) {
                    int eq = part.indexOf('=');
                    if (eq > 0) {
                        cookies.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
                    }
                }
            }
        }
        return cookies;
    }

    public String getCookie(String name) {
        return getCookies().get(name);
    }

    /** Locale from {@code Accept-Language}, else the server default. */
    public Locale getLocale() {
        String al = getHeader("Accept-Language");
        if (al != null) {
            String first = al.split(",")[0].trim();
            int semi = first.indexOf(';');
            if (semi > 0) {
                first = first.substring(0, semi).trim();
            }
            if (!first.isEmpty() && !first.equals("*")) {
                return Locale.forLanguageTag(first);
            }
        }
        return Locale.getDefault();
    }

    // ---- responses --------------------------------------------------------------------------------------------

    public void setHeader(String name, String value) {
        response.setHeader(name, value);
    }

    public void setCookie(String name, String value, long maxAgeSeconds, boolean httpOnly) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('=').append(value).append("; Path=").append(config.getContextPath().isEmpty() ? "/" : config.getContextPath());
        if (maxAgeSeconds >= 0) {
            sb.append("; Max-Age=").append(maxAgeSeconds);
        }
        sb.append("; SameSite=Lax");
        if (httpOnly) {
            sb.append("; HttpOnly");
        }
        if (isHttps()) {
            sb.append("; Secure");
        }
        response.addHeader("Set-Cookie", sb.toString());
    }

    public void send(int status, String contentType, String text) throws IOException {
        send(status, contentType, text.getBytes(StandardCharsets.UTF_8));
    }

    public void send(int status, String contentType, byte[] bytes) throws IOException {
        responded = true;
        response.setStatus(status);
        if (contentType != null) {
            response.setHeader("Content-Type", contentType);
        }
        if (!response.containsHeader("Cache-Control")) {
            response.setHeader("Cache-Control", "no-store");
        }
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (config.isHsts() && isHttps()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000");
        }
        boolean head = "HEAD".equals(request.getMethod());
        response.setContentLength(bytes.length);
        if (!head && bytes.length > 0) {
            OutputStream out = response.getOutputStream();
            out.write(bytes);
            out.flush();
        }
        response.flushBuffer();
    }

    public void sendJson(int status, String json) throws IOException {
        send(status, "application/json; charset=utf-8", json);
    }

    public void sendHtml(int status, String html) throws IOException {
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'");
        response.setHeader("X-Frame-Options", "DENY");
        send(status, "text/html; charset=utf-8", html);
    }

    public void sendText(int status, String text) throws IOException {
        send(status, "text/plain; charset=utf-8", text);
    }

    public void sendEmpty(int status) throws IOException {
        send(status, null, new byte[0]);
    }

    public void redirect(String location) throws IOException {
        response.setHeader("Location", location);
        sendEmpty(303);
    }

    public boolean isResponded() {
        return responded;
    }

    /** For handlers that write the response themselves (streamed downloads). */
    public void markResponded() {
        responded = true;
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    public static Map<String, String> parseForm(String encoded) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        if (encoded == null || encoded.isEmpty()) {
            return m;
        }
        for (String pair : encoded.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            m.put(decode(k), decode(v));
        }
        return m;
    }

    public static String decode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    public static String encode(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '&': sb.append("&amp;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Only a local path is accepted as a return URL; anything else becomes {@code /}. */
    public static String safeReturnUrl(String url) {
        if (url == null || url.isEmpty() || !url.startsWith("/") || url.startsWith("//") || url.contains("\\")
                || url.contains("\n") || url.contains("\r")) {
            return "/";
        }
        return url;
    }
}
