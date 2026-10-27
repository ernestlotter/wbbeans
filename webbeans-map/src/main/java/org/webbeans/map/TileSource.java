package org.webbeans.map;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a map's base tiles come from. Tiles are XYZ raster tiles ({@code z/x/y}, 256 px, Web Mercator). Four
 * kinds:
 * <ul>
 * <li>{@link #proxied} — the server fetches from a URL template and caches on disk under
 *     {@code <datadir>/system/tiles/<id>/}; the browser loads {@code /wb/tiles/<id>/z/x/y}. The default,
 *     because the application's Content Security Policy only allows images from the server itself, and
 *     because intranet browsers often have no route to the internet while the server has.</li>
 * <li>{@link #direct} — the browser loads the template itself; needs {@code ServerConfig.imageSources(origin)}.</li>
 * <li>{@link #directory} — tiles from a local folder ({@code <dir>/z/x/y.png}, as produced by tile tools) for
 *     air-gapped deployments.</li>
 * <li>{@link #blank} — no tiles; the browser draws a graticule. Tests and offline development.</li>
 * </ul>
 * Sources are registered by id when created so the tile endpoint can find them; create them once (a static
 * constant) and share.
 */
public final class TileSource {

    public enum Kind { PROXIED, DIRECT, DIRECTORY, BLANK }

    private static final Map<String, TileSource> REGISTRY = new ConcurrentHashMap<String, TileSource>();

    /** OpenStreetMap's standard tiles through the proxy (see their tile usage policy for heavy use). */
    public static final TileSource OSM = proxied("osm", "https://tile.openstreetmap.org/{z}/{x}/{y}.png", "© OpenStreetMap contributors")
            .setMaxZoom(19);
    /** No tiles at all: a graticule on the map background. */
    public static final TileSource BLANK = new TileSource("blank", Kind.BLANK, null, "", null);

    private final String id;
    private final Kind kind;
    private final String urlTemplate;
    private final String attribution;
    private final Path directory;
    private String subdomains = "";
    private int minZoom;
    private int maxZoom = 18;
    private String userAgent = "WebBeans/0.1 (+https://github.com/ernestlotter/wbbeans)";
    private int cacheDays = 30;
    private String contentType = "image/png";
    private String backgroundColor;

    private TileSource(String id, Kind kind, String urlTemplate, String attribution, Path directory) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,40}")) {
            throw new IllegalArgumentException("Tile source id must be [A-Za-z0-9_-]{1,40}: " + id);
        }
        this.id = id;
        this.kind = kind;
        this.urlTemplate = urlTemplate;
        this.attribution = attribution == null ? "" : attribution;
        this.directory = directory;
        REGISTRY.put(id, this);
    }

    /** Tiles fetched by the server from {@code template} ({@code {z} {x} {y} {s}}) and cached. */
    public static TileSource proxied(String id, String template, String attribution) {
        return new TileSource(id, Kind.PROXIED, template, attribution, null);
    }

    /** Tiles loaded by the browser straight from {@code template}; list the origin in {@code ServerConfig.imageSources}. */
    public static TileSource direct(String id, String template, String attribution) {
        return new TileSource(id, Kind.DIRECT, template, attribution, null);
    }

    /** Tiles from {@code dir/z/x/y.png} (or {@code .jpg}) on the server. */
    public static TileSource directory(String id, Path dir, String attribution) {
        return new TileSource(id, Kind.DIRECTORY, null, attribution, dir);
    }

    /** A registered source by id, or null. */
    public static TileSource get(String id) {
        return id == null ? null : REGISTRY.get(id);
    }

    public String getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public String getUrlTemplate() {
        return urlTemplate;
    }

    public String getAttribution() {
        return attribution;
    }

    public Path getDirectory() {
        return directory;
    }

    /** Letters substituted for {@code {s}} in turn ({@code "abc"}). */
    public TileSource setSubdomains(String s) {
        subdomains = s == null ? "" : s;
        return this;
    }

    public TileSource setZoomRange(int min, int max) {
        minZoom = Math.max(0, min);
        maxZoom = Math.max(minZoom, max);
        return this;
    }

    public TileSource setMaxZoom(int max) {
        return setZoomRange(minZoom, max);
    }

    public int getMinZoom() {
        return minZoom;
    }

    public int getMaxZoom() {
        return maxZoom;
    }

    /** The {@code User-Agent} the proxy identifies with (tile services require a real one). */
    public TileSource setUserAgent(String ua) {
        userAgent = ua;
        return this;
    }

    public String getUserAgent() {
        return userAgent;
    }

    /** How long a cached tile is served before it is fetched again; 0 = forever. */
    public TileSource setCacheDays(int days) {
        cacheDays = Math.max(0, days);
        return this;
    }

    public int getCacheDays() {
        return cacheDays;
    }

    public TileSource setContentType(String ct) {
        contentType = ct;
        return this;
    }

    public String getContentType() {
        return contentType;
    }

    /** The colour behind the tiles (CSS); null = the theme panel colour. */
    public TileSource setBackgroundColor(String css) {
        backgroundColor = css;
        return this;
    }

    /** The template with the coordinates substituted (direct sources and the proxy). */
    public String url(int z, int x, int y) {
        String u = urlTemplate.replace("{z}", String.valueOf(z)).replace("{x}", String.valueOf(x)).replace("{y}", String.valueOf(y));
        if (u.contains("{s}")) {
            String s = subdomains.isEmpty() ? "" : String.valueOf(subdomains.charAt(Math.abs(x + y) % subdomains.length()));
            u = u.replace("{s}", s);
        }
        return u;
    }

    /** The description the browser gets: where to load from and the limits. */
    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", id);
        m.put("kind", kind.name().toLowerCase(Locale.ROOT));
        if (kind == Kind.DIRECT) {
            m.put("url", urlTemplate);
            m.put("sub", subdomains);
        } else if (kind != Kind.BLANK) {
            m.put("url", TileContentProvider.PREFIX + id + "/{z}/{x}/{y}");
        }
        m.put("attr", attribution);
        m.put("min", minZoom);
        m.put("max", maxZoom);
        if (backgroundColor != null) {
            m.put("bg", backgroundColor);
        }
        return m;
    }

    @Override
    public String toString() {
        return "TileSource[" + id + " " + kind + "]";
    }
}
