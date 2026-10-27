package org.webbeans.map;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import org.webbeans.core.files.DataDir;
import org.webbeans.ui.ContentProvider;
import org.webbeans.ui.UISession;

/**
 * {@code GET /wb/tiles/<source>/<z>/<x>/<y>}: tiles of a {@link TileSource.Kind#PROXIED proxied} source,
 * fetched from the template once and cached under {@code <datadir>/system/tiles/<source>/z/x/y}, or of a
 * {@link TileSource.Kind#DIRECTORY directory} source read from its folder. Concurrent requests for the same
 * missing tile fetch it once. Signed-in users only (the front end checks).
 */
@ServiceProvider(service = ContentProvider.class)
public final class TileContentProvider implements ContentProvider {

    public static final String PREFIX = "/wb/tiles/";

    private static final Logger LOG = Logger.getLogger(TileContentProvider.class.getName());
    private static final Pattern PATH = Pattern.compile("([A-Za-z0-9_-]{1,40})/(\\d{1,2})/(\\d{1,8})/(\\d{1,8})(?:\\.[a-z]{3,4})?");
    private static final int TIMEOUT_MS = 10000;
    private static final int MAX_BYTES = 4 * 1024 * 1024;

    private final Map<String, Object> inFlight = new ConcurrentHashMap<String, Object>();
    private Path cacheRoot;

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    /** Where cached tiles go; default {@code <datadir>/system/tiles}. */
    public void setCacheRoot(Path root) {
        cacheRoot = root;
    }

    private Path cacheRoot() {
        if (cacheRoot == null) {
            DataDir d = Lookup.getDefault().lookup(DataDir.class);
            cacheRoot = d != null ? d.getSystemDir().resolve("tiles") : java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "wb-tiles");
        }
        return cacheRoot;
    }

    @Override
    public Content get(UISession session, String path, Map<String, String> query) throws IOException {
        Matcher m = PATH.matcher(path);
        if (!m.matches()) {
            return null;
        }
        TileSource src = TileSource.get(m.group(1));
        int z = Integer.parseInt(m.group(2));
        long x = Long.parseLong(m.group(3));
        long y = Long.parseLong(m.group(4));
        if (src == null || z < src.getMinZoom() || z > src.getMaxZoom() || x < 0 || y < 0 || x >= (1L << z) || y >= (1L << z)) {
            return null;
        }
        byte[] bytes;
        switch (src.getKind()) {
            case DIRECTORY:
                bytes = fromDirectory(src, z, x, y);
                break;
            case PROXIED:
                bytes = cached(src, z, x, y);
                break;
            default:
                return null;
        }
        return bytes == null ? null : new Content(bytes, src.getContentType(), "private, max-age=" + (src.getCacheDays() > 0 ? src.getCacheDays() * 86400L : 31536000L));
    }

    private static byte[] fromDirectory(TileSource src, int z, long x, long y) throws IOException {
        Path dir = src.getDirectory().resolve(String.valueOf(z)).resolve(String.valueOf(x));
        for (String ext : new String[] {"png", "jpg", "jpeg", "webp"}) {
            Path f = dir.resolve(y + "." + ext);
            if (Files.isRegularFile(f)) {
                return Files.readAllBytes(f);
            }
        }
        return null;
    }

    private byte[] cached(TileSource src, int z, long x, long y) throws IOException {
        Path f = cacheRoot().resolve(src.getId()).resolve(String.valueOf(z)).resolve(String.valueOf(x)).resolve(String.valueOf(y));
        if (Files.isRegularFile(f) && fresh(f, src)) {
            return Files.readAllBytes(f);
        }
        String key = src.getId() + "/" + z + "/" + x + "/" + y;
        Object lock = inFlight.get(key);
        if (lock == null) {
            lock = new Object();
            Object prev = inFlight.putIfAbsent(key, lock);
            if (prev != null) {
                lock = prev;
            }
        }
        try {
            synchronized (lock) {
                if (Files.isRegularFile(f) && fresh(f, src)) {
                    return Files.readAllBytes(f);
                }
                byte[] bytes;
                try {
                    bytes = fetch(src, src.url(z, (int) x, (int) y));
                } catch (IOException e) {
                    warnOnce(src, e);
                    bytes = null;
                }
                if (bytes == null) {
                    return Files.isRegularFile(f) ? Files.readAllBytes(f) : null;   // stale is better than nothing
                }
                Files.createDirectories(f.getParent());
                Path tmp = Files.createTempFile(f.getParent(), "tile", ".part");
                Files.write(tmp, bytes);
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return bytes;
            }
        } finally {
            inFlight.remove(key);
        }
    }

    private final Map<String, Long> warned = new ConcurrentHashMap<String, Long>();

    /** One warning per source per minute: an unreachable tile service would otherwise log once per tile. */
    private void warnOnce(TileSource src, IOException e) {
        long now = System.currentTimeMillis();
        Long last = warned.get(src.getId());
        if (last == null || now - last > 60000) {
            warned.put(src.getId(), now);
            LOG.log(Level.WARNING, "Tile source {0} unreachable: {1} (the map shows gaps; further failures this minute are not logged)",
                    new Object[] {src.getId(), e.toString()});
        } else {
            LOG.log(Level.FINE, "Tile fetch failed for " + src.getId() + ": " + e);
        }
    }

    private static boolean fresh(Path f, TileSource src) throws IOException {
        if (src.getCacheDays() <= 0) {
            return true;
        }
        long age = System.currentTimeMillis() - Files.getLastModifiedTime(f).toMillis();
        return age < src.getCacheDays() * 86400000L;
    }

    /** One HTTP fetch; null when the service answers with an error (logged, not thrown: the map just shows a gap). */
    static byte[] fetch(TileSource src, String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", src.getUserAgent());
        c.setRequestProperty("Accept", "image/*");
        c.setInstanceFollowRedirects(true);
        int status;
        try {
            status = c.getResponseCode();
        } catch (IOException e) {
            LOG.log(Level.FINE, "Tile fetch failed: " + url + ": " + e);
            throw e;
        }
        if (status != 200) {
            LOG.log(Level.FINE, "Tile {0} answered {1}", new Object[] {url, status});
            c.disconnect();
            return null;
        }
        InputStream in = c.getInputStream();
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) {
                buf.write(b, 0, n);
                if (buf.size() > MAX_BYTES) {
                    throw new IOException("Tile too large: " + url);
                }
            }
            return buf.toByteArray();
        } finally {
            in.close();
            c.disconnect();
        }
    }
}
