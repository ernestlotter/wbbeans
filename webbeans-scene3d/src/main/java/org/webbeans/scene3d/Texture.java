package org.webbeans.scene3d;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.webbeans.core.json.Json;
import org.webbeans.ui.BlobStore;

/**
 * An immutable image texture: either bytes put into the {@link BlobStore} ({@code /wb/blob/<id>}) or a module
 * resource ({@code /wb/res/<path>}). Sampling options return new instances; the id covers source and options.
 */
public final class Texture {

    public enum Wrap { CLAMP, REPEAT, MIRROR }

    public enum Filter { LINEAR, NEAREST }

    private final String url;
    private final double repeatX;
    private final double repeatY;
    private final Wrap wrap;
    private final Filter filter;
    private final boolean flipY;
    private final boolean srgb;
    private final String id;

    private Texture(String url, double repeatX, double repeatY, Wrap wrap, Filter filter, boolean flipY, boolean srgb) {
        this.url = url;
        this.repeatX = repeatX;
        this.repeatY = repeatY;
        this.wrap = wrap;
        this.filter = filter;
        this.flipY = flipY;
        this.srgb = srgb;
        this.id = "t" + BlobStore.hash(Json.write(toJson()).getBytes(StandardCharsets.UTF_8)).substring(0, 16);
    }

    /** An encoded image (PNG, JPEG, WebP ...). */
    public static Texture image(byte[] bytes, String contentType) {
        String blob = BlobStore.getDefault().put(bytes, contentType == null ? "image/png" : contentType);
        return new Texture("/wb/blob/" + blob, 1, 1, Wrap.CLAMP, Filter.LINEAR, true, true);
    }

    /** An image on the module class path, e.g. {@code org/example/textures/earth.jpg}. */
    public static Texture fromResource(String path) {
        return new Texture("/wb/res/" + path, 1, 1, Wrap.CLAMP, Filter.LINEAR, true, true);
    }

    public Texture repeat(double x, double y) {
        return new Texture(url, x, y, wrap == Wrap.CLAMP ? Wrap.REPEAT : wrap, filter, flipY, srgb);
    }

    public Texture wrap(Wrap w) {
        return new Texture(url, repeatX, repeatY, w, filter, flipY, srgb);
    }

    public Texture filter(Filter f) {
        return new Texture(url, repeatX, repeatY, wrap, f, flipY, srgb);
    }

    public Texture flipY(boolean b) {
        return new Texture(url, repeatX, repeatY, wrap, filter, b, srgb);
    }

    /** Whether the image holds colors (sRGB) rather than data such as normals; default true. */
    public Texture srgb(boolean b) {
        return new Texture(url, repeatX, repeatY, wrap, filter, flipY, b);
    }

    public String getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("url", url);
        if (repeatX != 1 || repeatY != 1) {
            m.put("repeat", java.util.Arrays.asList(repeatX, repeatY));
        }
        if (wrap != Wrap.CLAMP) {
            m.put("wrap", wrap.name().toLowerCase());
        }
        if (filter != Filter.LINEAR) {
            m.put("filter", filter.name().toLowerCase());
        }
        if (!flipY) {
            m.put("flipY", false);
        }
        if (!srgb) {
            m.put("srgb", false);
        }
        return m;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Texture && ((Texture) o).id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Texture[" + url + "]";
    }
}
