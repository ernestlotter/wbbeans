package org.webbeans.charts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An axis of a {@link Chart}: type, title, range, tick format (a {@code DecimalFormat} or
 * {@code DateTimeFormatter} pattern compiled server-side for the client), grid, and reference markers/bands.
 */
public final class Axis {

    /** A reference line ({@code to == null}) or band. */
    public static final class Marker {
        final String id;
        final double from;
        final Double to;
        final String label;
        final String color;

        Marker(String id, double from, Double to, String label, String color) {
            this.id = id;
            this.from = from;
            this.to = to;
            this.label = label;
            this.color = color;
        }
    }

    private final Runnable changed;
    private String title;
    private AxisType type = AxisType.LINEAR;
    private Double min;
    private Double max;
    private String format;
    private boolean grid = true;
    private boolean visible = true;
    private final Map<String, Marker> markers = new LinkedHashMap<String, Marker>();

    Axis(Runnable changed) {
        this.changed = changed;
    }

    public String getTitle() {
        return title;
    }

    public Axis setTitle(String t) {
        title = t;
        changed.run();
        return this;
    }

    public AxisType getType() {
        return type;
    }

    public Axis setType(AxisType t) {
        type = t == null ? AxisType.LINEAR : t;
        changed.run();
        return this;
    }

    public Double getMin() {
        return min;
    }

    public Axis setMin(Double v) {
        min = v;
        changed.run();
        return this;
    }

    public Double getMax() {
        return max;
    }

    public Axis setMax(Double v) {
        max = v;
        changed.run();
        return this;
    }

    public String getFormat() {
        return format;
    }

    /** A {@code DecimalFormat} pattern ({@code #,##0.0}, {@code #0'%'}) or, for time axes, a {@code DateTimeFormatter} pattern ({@code HH:mm:ss}). */
    public Axis setFormat(String pattern) {
        format = pattern;
        changed.run();
        return this;
    }

    public Axis setGrid(boolean b) {
        grid = b;
        changed.run();
        return this;
    }

    public boolean isGrid() {
        return grid;
    }

    public Axis setVisible(boolean b) {
        visible = b;
        changed.run();
        return this;
    }

    public boolean isVisible() {
        return visible;
    }

    /** A reference line at the value (replaces a marker with the same id). */
    public Axis addMarker(String id, double value, String label, String cssColor) {
        markers.put(id, new Marker(id, value, null, label, cssColor));
        changed.run();
        return this;
    }

    /** A shaded band between two values. */
    public Axis addBand(String id, double from, double to, String cssColor) {
        markers.put(id, new Marker(id, from, to, null, cssColor));
        changed.run();
        return this;
    }

    public Axis removeMarker(String id) {
        if (markers.remove(id) != null) {
            changed.run();
        }
        return this;
    }

    /** The wire form (a property value of the chart). */
    Map<String, Object> toJson(java.util.Locale locale) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("type", type.name().toLowerCase(java.util.Locale.ROOT));
        if (title != null) {
            m.put("title", title);
        }
        if (min != null) {
            m.put("min", min);
        }
        if (max != null) {
            m.put("max", max);
        }
        if (format != null) {
            m.put("format", FormatSpec.compile(format, type, locale));
        }
        m.put("grid", grid);
        m.put("visible", visible);
        if (!markers.isEmpty()) {
            List<Object> ms = new ArrayList<Object>();
            for (Marker k : markers.values()) {
                Map<String, Object> km = new LinkedHashMap<String, Object>();
                km.put("id", k.id);
                km.put("from", k.from);
                if (k.to != null) {
                    km.put("to", k.to);
                }
                if (k.label != null) {
                    km.put("label", k.label);
                }
                if (k.color != null) {
                    km.put("color", k.color);
                }
                ms.add(km);
            }
            m.put("markers", ms);
        }
        return m;
    }
}
