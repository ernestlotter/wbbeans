package org.webbeans.server.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.openide.util.Lookup;
import org.webbeans.core.files.DataDir;
import org.webbeans.server.HttpSession;
import org.webbeans.server.ServerConfig;
import org.webbeans.server.http.WbRequest;
import org.webbeans.ui.Component;
import org.webbeans.ui.UI;
import org.webbeans.ui.components.Upload;
import org.webbeans.ui.components.UploadedFile;

/**
 * {@code POST /wb/upload?ui=<uiId>&c=<componentId>&name=<file>&size=<bytes>}: the body is one file, raw (no
 * multipart — the browser sends one request per file with its MIME type as {@code Content-Type}). Authenticated by
 * the session cookie plus {@code X-WB-Token}. The file is streamed to {@code <datadir>/cache/uploads/} (or the
 * JVM's temp dir), then {@link Upload#received} runs on the session executor. Limits: the component's
 * {@code MAX_SIZE} and {@link ServerConfig#getMaxUploadSize()} → 413.
 */
@SuppressWarnings("serial") // servlets are nominally Serializable; this one is never serialised
public final class UploadHandler extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(UploadHandler.class.getName());

    private final ServerConfig config;
    private final AuthHandler auth;

    public UploadHandler(ServerConfig config, AuthHandler auth) {
        this.config = config;
        this.auth = auth;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        WbRequest r = new WbRequest(req, resp, config);
        try {
            route(r);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Upload failed", e);
            if (!r.isResponded()) {
                r.sendJson(500, "{\"error\":\"internal\"}");
            }
        } finally {
            if (!r.isResponded()) {
                r.sendJson(400, "{\"error\":\"bad request\"}");
            }
        }
    }

    private void route(WbRequest r) throws IOException {
        if (!r.getMethod().equals("POST")) {
            r.sendText(405, "Method not allowed");
            return;
        }
        HttpSession s = auth.currentSession(r);
        if (s == null) {
            r.sendJson(401, "{\"error\":\"not authenticated\"}");
            return;
        }
        String token = r.getHeader("X-WB-Token");
        if (token == null || !s.getCsrfToken().equals(token)) {
            r.sendJson(403, "{\"error\":\"bad token\"}");
            return;
        }
        final UI ui = s.getUI(r.getQueryParameter("ui"));
        if (ui == null || ui.getState() == UI.State.CLOSED) {
            r.sendJson(410, "{\"error\":\"gone\"}");
            return;
        }
        final int cid;
        try {
            cid = Integer.parseInt(r.getQueryParameter("c"));
        } catch (RuntimeException e) {
            r.sendJson(400, "{\"error\":\"component\"}");
            return;
        }
        long declared = -1;
        try {
            declared = Long.parseLong(String.valueOf(r.getQueryParameter("size")));
        } catch (RuntimeException e) {
            // unknown size: the stream is bounded below anyway
        }
        long hardLimit = config.getMaxUploadSize();
        long contentLength = r.getRequest().getContentLengthLong();
        if (declared > hardLimit || contentLength > hardLimit) {
            r.sendJson(413, "{\"error\":\"too large\"}");
            return;
        }
        String name = safeName(r.getQueryParameter("name"));
        String type = r.getHeader("Content-Type");
        if (type == null || type.isEmpty()) {
            type = "application/octet-stream";
        }
        s.touch();
        Path tmp = Files.createTempFile(uploadDir(), "up-", ".bin");
        long size = 0;
        boolean ok = false;
        try {
            InputStream in = r.getRequest().getInputStream();
            OutputStream out = Files.newOutputStream(tmp);
            try {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    size += n;
                    if (size > hardLimit) {
                        r.sendJson(413, "{\"error\":\"too large\"}");
                        return;
                    }
                    out.write(buf, 0, n);
                }
            } finally {
                out.close();
            }
            ok = true;
        } finally {
            if (!ok) {
                Files.deleteIfExists(tmp);
            }
        }
        final UploadedFile file = new UploadedFile(name, size, type, tmp);
        ui.access(new Runnable() {
            @Override
            public void run() {
                Component c = ui.findComponent(cid);
                if (c instanceof Upload) {
                    Long max = c.get(Upload.MAX_SIZE);
                    if (max != null && file.getSize() > max) {
                        LOG.log(Level.INFO, "Upload {0} rejected: larger than {1}", new Object[] {file, max});
                        file.delete();
                        return;
                    }
                    ((Upload) c).received(file);
                } else {
                    LOG.log(Level.INFO, "Upload for unknown component {0} dropped", cid);
                    file.delete();
                }
            }
        });
        r.sendJson(200, "{\"ok\":true,\"size\":" + size + "}");
    }

    private Path uploadDir() throws IOException {
        DataDir dd = Lookup.getDefault().lookup(DataDir.class);
        Path dir = dd != null ? dd.getCacheDir().resolve("uploads") : Files.createTempDirectory("wb-uploads");
        Files.createDirectories(dir);
        return dir;
    }

    /** Strips directories and control characters from a browser-supplied file name. */
    static String safeName(String name) {
        if (name == null || name.isEmpty()) {
            return "upload";
        }
        String n = name;
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) {
            n = n.substring(slash + 1);
        }
        StringBuilder sb = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            sb.append(c < 32 || c == 127 || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|' ? '_' : c);
        }
        String out = sb.toString().trim();
        return out.isEmpty() || out.equals(".") || out.equals("..") ? "upload" : out;
    }
}
