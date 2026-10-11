package org.webbeans.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.Lookup;
import org.webbeans.core.registry.Entry;
import org.webbeans.core.registry.Registry;

/**
 * Assembles the client application from the registry: {@code wb-core.js} followed by every renderer registered under
 * {@code Renderers/} (attribute {@code resource}), and {@code wb.css} followed by the renderers' {@code css}
 * resources. The result is content-hashed so it can be cached forever under {@code /wb/app-<hash>.js}.
 */
public final class Bundler {

    private static final Logger LOG = Logger.getLogger(Bundler.class.getName());
    static final String CORE_JS = "org/webbeans/ui/client/wb-core.js";
    static final String CORE_CSS = "org/webbeans/ui/client/wb.css";

    /** One built bundle: immutable, shared between requests. */
    public static final class Bundle {
        private final byte[] js;
        private final byte[] css;
        private final String hash;

        Bundle(byte[] js, byte[] css, String hash) {
            this.js = js;
            this.css = css;
            this.hash = hash;
        }

        public byte[] getJs() {
            return js;
        }

        public byte[] getCss() {
            return css;
        }

        /** 16 hex characters of the SHA-256 over both files. */
        public String getHash() {
            return hash;
        }

        public String getJsName() {
            return "app-" + hash + ".js";
        }

        public String getCssName() {
            return "app-" + hash + ".css";
        }
    }

    private final ClassLoader fallback;

    public Bundler() {
        this(Bundler.class.getClassLoader());
    }

    public Bundler(ClassLoader fallback) {
        this.fallback = fallback;
    }

    public Bundle build() {
        StringBuilder js = new StringBuilder(256 * 1024);
        StringBuilder css = new StringBuilder(64 * 1024);
        ClassLoader system = loader(null);
        append(js, system, CORE_JS, "/* wb-core */");
        append(css, system, CORE_CSS, "/* wb.css */");
        Set<String> seenJs = new LinkedHashSet<String>();
        Set<String> seenCss = new LinkedHashSet<String>();
        List<Entry> entries = new ArrayList<Entry>(Registry.getDefault().folder("Renderers").getEntries());
        for (Entry e : entries) {
            Entry t = e.getTarget();
            if (t == null) {
                continue;
            }
            ClassLoader l = loader(t);
            String res = t.getString("resource");
            if (res != null && !res.isEmpty() && seenJs.add(res)) {
                append(js, l, res, "\n/* renderer " + t.getName() + " (" + res + ") */");
            }
            String c = t.getString("css");
            if (c != null && !c.isEmpty() && seenCss.add(c)) {
                append(css, l, c, "\n/* " + t.getName() + " (" + c + ") */");
            }
        }
        byte[] jsBytes = js.toString().getBytes(StandardCharsets.UTF_8);
        byte[] cssBytes = css.toString().getBytes(StandardCharsets.UTF_8);
        String hash = hash(jsBytes, cssBytes);
        LOG.log(Level.INFO, "Client bundle app-{0}: {1} renderers, {2} KB js, {3} KB css",
                new Object[] {hash, seenJs.size(), jsBytes.length / 1024, cssBytes.length / 1024});
        return new Bundle(jsBytes, cssBytes, hash);
    }

    private ClassLoader loader(Entry e) {
        if (e != null && e.getModule() != null && e.getModule().getClassLoader() != null) {
            return e.getModule().getClassLoader();
        }
        ClassLoader system = Lookup.getDefault().lookup(ClassLoader.class);
        return system != null ? system : fallback;
    }

    private void append(StringBuilder sb, ClassLoader l, String resource, String banner) {
        InputStream in = l.getResourceAsStream(resource);
        if (in == null) {
            in = fallback.getResourceAsStream(resource);
        }
        if (in == null) {
            LOG.log(Level.WARNING, "Client resource {0} not found", resource);
            sb.append(banner).append("\n/* MISSING: ").append(resource).append(" */\n");
            return;
        }
        try {
            try {
                sb.append(banner).append('\n').append(new String(readAll(in), StandardCharsets.UTF_8)).append('\n');
            } finally {
                in.close();
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot read " + resource, e);
        }
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static String hash(byte[] a, byte[] b) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(a);
            md.update(b);
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", d[i] & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
