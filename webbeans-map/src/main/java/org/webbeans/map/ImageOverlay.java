package org.webbeans.map;

import java.util.Map;

import org.webbeans.ui.Resource;

/** A georeferenced image stretched over a box (a floor plan, a radar frame, a scanned map). */
public class ImageOverlay extends Overlay {

    private LatLngBounds bounds;
    private Resource image;
    private String url;

    ImageOverlay(String id, LatLngBounds bounds) {
        super(id);
        this.bounds = bounds;
    }

    public LatLngBounds getBounds() {
        return bounds;
    }

    public ImageOverlay setBounds(LatLngBounds b) {
        bounds = b;
        changed();
        return this;
    }

    /** An image resource of the application. */
    public ImageOverlay setImage(Resource r) {
        image = r;
        url = null;
        changed();
        return this;
    }

    /** A server-relative URL (a {@code /wb/blob/<id>} from the {@code BlobStore} for generated images). */
    public ImageOverlay setImageUrl(String serverUrl) {
        url = serverUrl;
        image = null;
        changed();
        return this;
    }

    @Override
    public ImageOverlay setOpacity(double o) {
        super.setOpacity(o);
        return this;
    }

    @Override
    public ImageOverlay setLayer(String layerId) {
        super.setLayer(layerId);
        return this;
    }

    @Override
    public ImageOverlay setInteractive(boolean b) {
        super.setInteractive(b);
        return this;
    }

    @Override
    String kind() {
        return "image";
    }

    @Override
    void writeJson(Map<String, Object> m) {
        m.put("b", bounds.toJson());
        put(m, "i", image);
        put(m, "url", url);
    }
}
