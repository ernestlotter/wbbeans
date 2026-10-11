package org.webbeans.ui.components;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.DownloadService;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Resource;

/**
 * Shows one image with zoom (wheel, buttons, fit / 1:1), panning by drag, rotation and an optional toolbar —
 * for photos, scans and generated charts. The source is a module {@link Resource}, any URL, or bytes served
 * through the {@link DownloadService}.
 */
@Renderer(id = "wb.ImageViewer", resource = "org/webbeans/ui/client/renderers/viewers.js", css = "org/webbeans/ui/client/renderers/viewers.css")
public class ImageViewer extends Component {

    public static final Property<Object> SOURCE = Property.of("source", Object.class, null);
    public static final Property<String> ALT = Property.of("alt", String.class, "");
    public static final Property<Boolean> TOOLBAR = Property.of("toolbar", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> CHECKERBOARD = Property.of("checkerboard", Boolean.class, Boolean.FALSE);
    public static final Property<Double> ZOOM = Property.of("zoom", Double.class, null).clientOwned();
    public static final Property<Integer> ROTATION = Property.of("rotation", Integer.class, Integer.valueOf(0));

    static final EventType<Event> ZOOM_CHANGE = EventType.of("zoom", Event::new);

    public ImageViewer() {
        super("wb.ImageViewer");
        addListener(ZOOM_CHANGE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                Object v = e.getData().get("value");
                setFromClient(ZOOM, v instanceof Number ? Double.valueOf(((Number) v).doubleValue()) : null);
            }
        });
    }

    public ImageViewer(Resource source) {
        this();
        setSource(source);
    }

    public ImageViewer setSource(Resource r) {
        set(SOURCE, r);
        return this;
    }

    /** Any URL the browser can load (same origin, https, or data:). */
    public ImageViewer setUrl(String url) {
        set(SOURCE, url);
        return this;
    }

    /** Image bytes served from this session (needs a current UI). */
    public ImageViewer setImage(final byte[] bytes, final String contentType) {
        final String name = "image";
        set(SOURCE, DownloadService.url(new java.util.function.Supplier<DownloadService.Content>() {
            @Override
            public DownloadService.Content get() {
                return DownloadService.content(bytes, name, contentType);
            }
        }));
        return this;
    }

    public ImageViewer setAlt(String alt) {
        set(ALT, alt);
        return this;
    }

    public ImageViewer setToolbar(boolean b) {
        set(TOOLBAR, b);
        return this;
    }

    /** A checkerboard behind transparent images. */
    public ImageViewer setCheckerboard(boolean b) {
        set(CHECKERBOARD, b);
        return this;
    }

    /** Rotation in degrees (multiples of 90). */
    public ImageViewer setRotation(int degrees) {
        set(ROTATION, ((degrees % 360) + 360) % 360);
        return this;
    }

    public ImageViewer rotate() {
        return setRotation(get(ROTATION) + 90);
    }

    /** A zoom factor (1 = natural size), or {@code null} to fit. */
    public ImageViewer setZoom(Double factor) {
        set(ZOOM, factor);
        return this;
    }

    public void fit() {
        set(ZOOM, null);
        call("fit");
    }
}
