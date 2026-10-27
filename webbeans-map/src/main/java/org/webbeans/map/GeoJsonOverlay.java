package org.webbeans.map;

import java.util.Map;

import org.webbeans.core.json.Json;
import org.webbeans.ui.Resource;

/**
 * A GeoJSON document (FeatureCollection, Feature or geometry) drawn with one style; a feature's own
 * {@code properties.style} object ({@code color, weight, dash, fillColor, fillOpacity, fill, icon, label, tooltip})
 * overrides it. Clicks and hovers report the feature index ({@code WidgetEvent.getFeature()}), and the feature's
 * {@code id} or {@code properties.id} when present.
 */
public class GeoJsonOverlay extends PathOverlay {

    private Object geoJson;
    private Resource pointIcon;
    private boolean pointsAsCircles;
    private String labelProperty;
    private String tooltipProperty;

    GeoJsonOverlay(String id, Object geoJson) {
        super(id);
        this.geoJson = geoJson;
    }

    /** The document as a JSON string. */
    public GeoJsonOverlay setGeoJson(String json) {
        geoJson = Json.parseObject(json);
        changed();
        return this;
    }

    /** The document as maps and lists (what {@code Json.parseObject} returns). */
    public GeoJsonOverlay setGeoJson(Map<String, Object> doc) {
        geoJson = doc;
        changed();
        return this;
    }

    public Object getGeoJson() {
        return geoJson;
    }

    /** The icon for Point features; null = the default pin. */
    public GeoJsonOverlay setPointIcon(Resource r) {
        pointIcon = r;
        changed();
        return this;
    }

    /** Draw Point features as small circles instead of markers. */
    public GeoJsonOverlay setPointsAsCircles(boolean b) {
        pointsAsCircles = b;
        changed();
        return this;
    }

    /** The feature property shown as a label next to points. */
    public GeoJsonOverlay setLabelProperty(String name) {
        labelProperty = name;
        changed();
        return this;
    }

    /** The feature property used as the tooltip. */
    public GeoJsonOverlay setTooltipProperty(String name) {
        tooltipProperty = name;
        changed();
        return this;
    }

    @Override
    public GeoJsonOverlay setColor(String css) {
        super.setColor(css);
        return this;
    }

    @Override
    public GeoJsonOverlay setWeight(double px) {
        super.setWeight(px);
        return this;
    }

    @Override
    public GeoJsonOverlay setFillColor(String css) {
        super.setFillColor(css);
        return this;
    }

    @Override
    public GeoJsonOverlay setFillOpacity(double o) {
        super.setFillOpacity(o);
        return this;
    }

    @Override
    public GeoJsonOverlay setLayer(String layerId) {
        super.setLayer(layerId);
        return this;
    }

    @Override
    public GeoJsonOverlay setInteractive(boolean b) {
        super.setInteractive(b);
        return this;
    }

    @Override
    String kind() {
        return "geojson";
    }

    @Override
    void writeJson(Map<String, Object> m) {
        m.put("geo", geoJson);
        writeStyle(m);
        put(m, "icon", pointIcon);
        if (pointsAsCircles) {
            m.put("circles", true);
        }
        put(m, "lp", labelProperty);
        put(m, "tp", tooltipProperty);
    }
}
