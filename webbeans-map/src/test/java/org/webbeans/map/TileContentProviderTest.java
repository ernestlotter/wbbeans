package org.webbeans.map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.webbeans.ui.ContentProvider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/** The tile endpoint: directory sources, the proxy with its disk cache, path validation. */
public class TileContentProviderTest {

    private Path dir;
    private HttpServer http;
    private final AtomicInteger fetches = new AtomicInteger();
    private final byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("wb-tiles");
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/t/", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                fetches.incrementAndGet();
                String ua = ex.getRequestHeaders().getFirst("User-Agent");
                if (ex.getRequestURI().getPath().endsWith("/9/9.png") || ua == null || !ua.startsWith("WebBeans")) {
                    ex.sendResponseHeaders(404, -1);
                    return;
                }
                ex.getResponseHeaders().add("Content-Type", "image/png");
                ex.sendResponseHeaders(200, png.length);
                OutputStream out = ex.getResponseBody();
                out.write(png);
                out.close();
            }
        });
        http.start();
    }

    @After
    public void tearDown() throws Exception {
        http.stop(0);
        Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(new java.util.function.Consumer<Path>() {
            @Override
            public void accept(Path p) {
                p.toFile().delete();
            }
        });
    }

    @Test
    public void directorySourceServesFilesAndRejectsBadPaths() throws Exception {
        Path tiles = dir.resolve("local");
        Files.createDirectories(tiles.resolve("3/4"));
        Files.write(tiles.resolve("3/4/5.png"), png);
        TileSource src = TileSource.directory("test-local", tiles, "Local").setMaxZoom(10);
        TileContentProvider p = new TileContentProvider();
        assertEquals("/wb/tiles/", p.getPrefix());
        ContentProvider.Content c = p.get(null, "test-local/3/4/5", Collections.<String, String>emptyMap());
        assertNotNull(c);
        assertArrayEquals(png, c.getBytes());
        assertEquals("image/png", c.getContentType());
        assertTrue(c.getCacheControl().startsWith("private, max-age="));
        assertNotNull(p.get(null, "test-local/3/4/5.png", Collections.<String, String>emptyMap()));
        assertNull(p.get(null, "test-local/3/4/6", Collections.<String, String>emptyMap()));
        assertNull("beyond max zoom", p.get(null, "test-local/11/0/0", Collections.<String, String>emptyMap()));
        assertNull("x out of range", p.get(null, "test-local/3/8/0", Collections.<String, String>emptyMap()));
        assertNull("no traversal", p.get(null, "test-local/3/../4/5", Collections.<String, String>emptyMap()));
        assertNull("unknown source", p.get(null, "nope/3/4/5", Collections.<String, String>emptyMap()));
        assertNull("blank source has no tiles", p.get(null, "blank/0/0/0", Collections.<String, String>emptyMap()));
        assertEquals("/wb/tiles/test-local/{z}/{x}/{y}", src.toJson().get("url"));
    }

    @Test
    public void proxiedSourceFetchesOnceAndCachesOnDisk() throws Exception {
        String base = "http://127.0.0.1:" + http.getAddress().getPort() + "/t/{z}/{x}/{y}.png";
        TileSource src = TileSource.proxied("test-proxy", base, "Test");
        TileContentProvider p = new TileContentProvider();
        p.setCacheRoot(dir.resolve("cache"));
        ContentProvider.Content c = p.get(null, "test-proxy/5/6/7", Collections.<String, String>emptyMap());
        assertNotNull(c);
        assertArrayEquals(png, c.getBytes());
        assertEquals(1, fetches.get());
        assertTrue(Files.isRegularFile(dir.resolve("cache/test-proxy/5/6/7")));
        // second request: from the cache
        assertNotNull(p.get(null, "test-proxy/5/6/7", Collections.<String, String>emptyMap()));
        assertEquals(1, fetches.get());
        // upstream 404 → no tile, nothing cached
        assertNull(p.get(null, "test-proxy/9/9/9", Collections.<String, String>emptyMap()));
        assertEquals(2, fetches.get());
        assertTrue(!Files.exists(dir.resolve("cache/test-proxy/9/9/9")));
        // direct sources are the browser's business
        TileSource direct = TileSource.direct("test-direct", "https://{s}.example.org/{z}/{x}/{y}.png", "Ex").setSubdomains("abc");
        assertNull(p.get(null, "test-direct/1/1/1", Collections.<String, String>emptyMap()));
        assertEquals("https://b.example.org/1/1/0.png", direct.url(1, 1, 0));
        assertEquals("https://{s}.example.org/{z}/{x}/{y}.png", direct.toJson().get("url"));
        assertEquals("abc", direct.toJson().get("sub"));
    }
}
