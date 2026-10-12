package org.webbeans.server.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.openide.util.Lookup;
import org.webbeans.core.json.Json;
import org.webbeans.server.Bundler;
import org.webbeans.server.HttpSession;
import org.webbeans.server.ServerConfig;
import org.webbeans.server.http.WbRequest;
import org.webbeans.ui.DownloadService;
import org.webbeans.ui.PageProvider;
import org.webbeans.ui.UI;
import org.webbeans.ui.UISession;

/**
 * The application page and its static files.
 * <ul>
 * <li>{@code GET /}: creates a {@link UI} (one per browser tab) in the caller's session and serves the bootstrap
 * page that loads the client bundle; unauthenticated callers are sent to {@code /login}.</li>
 * <li>{@code GET /wb/app-<hash>.js|css}: the bundled client, cacheable forever by hash.</li>
 * <li>{@code GET /wb/res/<path>}: a resource from the module class path (icons, provider scripts).</li>
 * <li>{@code GET /wb/blob/<sha256>}: a content-addressed binary payload from the {@code BlobStore} (geometry, textures).</li>
 * <li>{@code GET /wb/download/<token>}: a file offered by {@code DownloadService} in one of the session's tabs.</li>
 * <li>{@code GET <prefix>...}: a page of a {@link PageProvider} service (the help module's {@code /wb/help/<id>}), for signed-in users.</li>
 * </ul>
 */
@SuppressWarnings("serial") // servlets are nominally Serializable; these are never serialised
public final class AppHandler extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(AppHandler.class.getName());

    private final ServerConfig config;
    private final AuthHandler auth;
    private final Bundler bundler;
    private volatile Bundler.Bundle bundle;

    public AppHandler(ServerConfig config, AuthHandler auth, Bundler bundler) {
        this.config = config;
        this.auth = auth;
        this.bundler = bundler;
    }

    /** Discards the built bundle; the next page load rebuilds it (called when modules change). */
    public void invalidateBundle() {
        bundle = null;
    }

    /** The hash of the bundle as built so far, or null when none has been built (nobody loaded a page yet). */
    public String currentBundleHash() {
        Bundler.Bundle b = bundle;
        return b == null ? null : b.getHash();
    }

    public Bundler.Bundle bundle() {
        Bundler.Bundle b = bundle;
        if (b == null || config.isDevMode()) {
            synchronized (this) {
                b = bundle;
                if (b == null || config.isDevMode()) {
                    b = bundler.build();
                    bundle = b;
                }
            }
        }
        return b;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        WbRequest r = new WbRequest(req, resp, config);
        try {
            route(r);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Request failed: " + r.getMethod() + " " + r.getPath(), e);
            if (!r.isResponded()) {
                r.sendText(500, "Internal error");
            }
        } finally {
            if (!r.isResponded()) {
                r.sendText(404, "Not found");
            }
        }
    }

    private void route(WbRequest r) throws IOException {
        String path = r.getPath();
        if (!r.getMethod().equals("GET") && !r.getMethod().equals("HEAD")) {
            r.sendText(405, "Method not allowed");
            return;
        }
        if (path.equals("/") || path.equals("/index.html")) {
            page(r);
        } else if (path.startsWith("/wb/app-") && (path.endsWith(".js") || path.endsWith(".css"))) {
            bundleFile(r, path);
        } else if (path.startsWith("/wb/res/")) {
            resource(r, path.substring("/wb/res/".length()));
        } else if (path.startsWith("/wb/blob/")) {
            blob(r, path.substring("/wb/blob/".length()));
        } else if (path.startsWith("/wb/download/")) {
            download(r, path.substring("/wb/download/".length()));
        } else if (path.equals("/favicon.ico")) {
            r.sendEmpty(204);
        } else {
            for (PageProvider p : Lookup.getDefault().lookupAll(PageProvider.class)) {
                if (path.startsWith(p.getPrefix())) {
                    providerPage(r, p, path.substring(p.getPrefix().length()));
                    return;
                }
            }
        }
    }

    /** A module page ({@code /wb/help/...}): signed-in users only, rendered on the session executor. */
    private void providerPage(WbRequest r, final PageProvider p, String sub) throws IOException {
        HttpSession s = auth.currentSession(r);
        if (s == null) {
            r.redirect(config.getContextPath() + "/login?return=" + WbRequest.encode(r.getPath()));
            return;
        }
        s.touch();
        final UISession us = s.getUISession();
        final String path = sub;
        final Map<String, String> query = r.getQuery();
        final String[] html = new String[1];
        try {
            us.access(new Runnable() {
                @Override
                public void run() {
                    html[0] = p.render(us, config.getContextPath(), path, query);
                }
            }).get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            r.sendText(503, "Interrupted");
            return;
        } catch (java.util.concurrent.ExecutionException e) {
            LOG.log(Level.SEVERE, "Page provider failed: " + r.getPath(), e.getCause());
            r.sendText(500, "Internal error");
            return;
        } catch (java.util.concurrent.TimeoutException e) {
            r.sendText(503, "Busy");
            return;
        }
        if (html[0] == null) {
            r.sendText(404, "Not found");
        } else {
            r.sendHtml(200, html[0]);
        }
    }

    private void page(WbRequest r) throws IOException {
        HttpSession s = auth.currentSession(r);
        if (s == null) {
            String ret = r.getRawQuery() == null ? "/" : "/?" + r.getRawQuery();
            r.redirect(config.getContextPath() + "/login?return=" + WbRequest.encode(ret));
            return;
        }
        s.touch();
        Bundler.Bundle b = bundle();
        String uiId = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        s.getUISession().createUI(uiId);
        Locale locale = s.getLocale();
        String cp = config.getContextPath();
        Map<String, Object> cfg = new LinkedHashMap<String, Object>();
        cfg.put("uiId", uiId);
        cfg.put("token", s.getCsrfToken());
        cfg.put("locale", locale.toLanguageTag());
        cfg.put("base", cp);
        cfg.put("poll", "/wb/poll");
        cfg.put("ws", config.isWebSocketEnabled() ? "/wb/ws" : null);
        cfg.put("user", s.getPrincipal().getName());
        cfg.put("dev", Boolean.valueOf(config.isDevMode()));
        String title = WbRequest.escapeHtml(config.getApplicationTitle());
        StringBuilder html = new StringBuilder(1024);
        html.append("<!doctype html><html lang=\"").append(WbRequest.escapeHtml(locale.getLanguage())).append("\"><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
            .append("<title>").append(title).append("</title>")
            .append("<link rel=\"stylesheet\" href=\"").append(cp).append("/wb/").append(b.getCssName()).append("\">")
            .append("</head><body><div id=\"wb-root\"></div><div id=\"wb-conn\" class=\"wb-hidden\"></div>")
            .append("<script>window.WB = ").append(Json.write(cfg)).append(";</script>")
            .append("<script src=\"").append(cp).append("/wb/").append(b.getJsName()).append("\"></script>")
            .append("</body></html>");
        r.sendHtml(200, html.toString());
    }

    private void bundleFile(WbRequest r, String path) throws IOException {
        Bundler.Bundle b = bundle();
        String name = path.substring("/wb/".length());
        boolean js = name.endsWith(".js");
        boolean current = name.equals(js ? b.getJsName() : b.getCssName());
        if (current && !config.isDevMode()) {
            r.setHeader("Cache-Control", "public, max-age=31536000, immutable");
        }
        r.send(200, js ? "application/javascript; charset=utf-8" : "text/css; charset=utf-8", js ? b.getJs() : b.getCss());
    }

    private void resource(WbRequest r, String res) throws IOException {
        if (res.isEmpty() || res.contains("..") || res.startsWith("/") || res.endsWith(".class")
                || res.startsWith("META-INF") || res.endsWith(".properties")) {
            r.sendText(404, "Not found");
            return;
        }
        ClassLoader l = Lookup.getDefault().lookup(ClassLoader.class);
        if (l == null) {
            l = getClass().getClassLoader();
        }
        InputStream in = l.getResourceAsStream(res);
        if (in == null) {
            r.sendText(404, "Not found");
            return;
        }
        byte[] bytes;
        try {
            bytes = readAll(in);
        } finally {
            in.close();
        }
        if (!config.isDevMode()) {
            r.setHeader("Cache-Control", "public, max-age=3600");
        }
        r.send(200, contentType(res), bytes);
    }

    /** {@code GET /wb/blob/<id>}: a content-addressed payload ({@link org.webbeans.ui.BlobStore}); needs a session. */
    private void blob(WbRequest r, String id) throws IOException {
        if (auth.currentSession(r) == null) {
            r.sendText(401, "Not authenticated");
            return;
        }
        if (!id.matches("[0-9a-f]{64}")) {
            r.sendText(404, "Not found");
            return;
        }
        org.webbeans.ui.BlobStore.Blob b = org.webbeans.ui.BlobStore.getDefault().get(id);
        if (b == null) {
            r.sendText(404, "Not found");
            return;
        }
        r.setHeader("Cache-Control", "private, max-age=31536000, immutable");
        r.send(200, b.getContentType(), b.getBytes());
    }

    /** {@code GET /wb/download/<token>}: streams a {@link DownloadService} entry of one of the session's tabs. */
    private void download(WbRequest r, String token) throws IOException {
        HttpSession s = auth.currentSession(r);
        if (s == null) {
            r.sendText(401, "Not authenticated");
            return;
        }
        if (!token.matches("[A-Za-z0-9_-]{8,64}")) {
            r.sendText(404, "Not found");
            return;
        }
        DownloadService.Content c = null;
        for (UI ui : s.getUISession().getUIs()) {
            c = DownloadService.take(ui, token);
            if (c != null) {
                break;
            }
        }
        if (c == null) {
            r.sendText(404, "Not found");
            return;
        }
        s.touch();
        javax.servlet.http.HttpServletResponse resp = r.getResponse();
        resp.setStatus(200);
        resp.setHeader("Content-Type", c.getContentType() == null ? "application/octet-stream" : c.getContentType());
        resp.setHeader("Content-Disposition", contentDisposition(c.getFileName()));
        resp.setHeader("Cache-Control", "private, no-store");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        if (c.getLength() >= 0) {
            resp.setContentLengthLong(c.getLength());
        }
        r.markResponded();
        InputStream in = c.open();
        try {
            java.io.OutputStream out = resp.getOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } finally {
            in.close();
        }
    }

    /** {@code attachment; filename="ascii"; filename*=UTF-8''...} (RFC 6266 / 5987). */
    static String contentDisposition(String fileName) {
        String name = fileName == null || fileName.isEmpty() ? "download" : fileName;
        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            ascii.append(ch < 32 || ch > 126 || ch == '"' || ch == '\\' ? '_' : ch);
        }
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + WbRequest.encode(name).replace("+", "%20");
    }

    static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(16 * 1024);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    static String contentType(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (n.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (n.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (n.endsWith(".png")) {
            return "image/png";
        }
        if (n.endsWith(".gif")) {
            return "image/gif";
        }
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (n.endsWith(".ico")) {
            return "image/x-icon";
        }
        if (n.endsWith(".woff2")) {
            return "font/woff2";
        }
        if (n.endsWith(".woff")) {
            return "font/woff";
        }
        if (n.endsWith(".json")) {
            return "application/json";
        }
        if (n.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (n.endsWith(".txt") || n.endsWith(".md")) {
            return "text/plain; charset=utf-8";
        }
        return "application/octet-stream";
    }
}
