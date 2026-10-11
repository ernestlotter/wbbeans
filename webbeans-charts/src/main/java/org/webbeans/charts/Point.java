package org.webbeans.charts;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One data point: an x (a number, an instant as epoch millis, or a category name), a y, and optional size
 * (bubbles), key (for {@link Series#updatePoint}), label, colour and metadata. Immutable; the {@code with…}
 * methods return copies.
 */
public final class Point {

    private final double x;
    private final String category;
    private final double y;
    private final Double size;
    private final Object key;
    private final String label;
    private final String color;
    private final Map<String, Object> meta;

    private Point(double x, String category, double y, Double size, Object key, String label, String color, Map<String, Object> meta) {
        this.x = x;
        this.category = category;
        this.y = y;
        this.size = size;
        this.key = key;
        this.label = label;
        this.color = color;
        this.meta = meta;
    }

    public static Point of(double x, double y) {
        return new Point(x, null, y, null, null, null, null, null);
    }

    public static Point of(Instant x, double y) {
        return new Point(x.toEpochMilli(), null, y, null, null, null, null, null);
    }

    /** A point on a category axis; unknown categories are appended to the model's category list. */
    public static Point of(String category, double y) {
        if (category == null) {
            throw new NullPointerException("category");
        }
        return new Point(0, category, y, null, null, null, null, null);
    }

    public Point withKey(Object key) {
        return new Point(x, category, y, size, key, label, color, meta);
    }

    public Point withSize(double size) {
        return new Point(x, category, y, size, key, label, color, meta);
    }

    public Point withLabel(String label) {
        return new Point(x, category, y, size, key, label, color, meta);
    }

    /** A CSS colour for this point only (bar, pie slice, marker). */
    public Point withColor(String cssColor) {
        return new Point(x, category, y, size, key, label, cssColor, meta);
    }

    public Point withMeta(Map<String, Object> meta) {
        return new Point(x, category, y, size, key, label, color, meta == null ? null : new LinkedHashMap<String, Object>(meta));
    }

    public double getX() {
        return x;
    }

    /** The category name, or null for numeric/time points. */
    public String getCategory() {
        return category;
    }

    public double getY() {
        return y;
    }

    public Double getSize() {
        return size;
    }

    public Object getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }

    public Map<String, Object> getMeta() {
        return meta == null ? Collections.<String, Object>emptyMap() : Collections.unmodifiableMap(meta);
    }

    /** Wire form: {@code [x, y]}, {@code [x, y, size]} or an object when keyed/labelled/coloured; {@code x} resolved by the model. */
    Object toJson(double resolvedX) {
        if (key == null && label == null && color == null && meta == null) {
            return size == null ? new Object[] {resolvedX, y} : new Object[] {resolvedX, y, size};
        }
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("x", resolvedX);
        m.put("y", y);
        if (size != null) {
            m.put("s", size);
        }
        if (key != null) {
            m.put("k", String.valueOf(key));
        }
        if (label != null) {
            m.put("l", label);
        }
        if (color != null) {
            m.put("c", color);
        }
        if (meta != null) {
            m.put("m", meta);
        }
        return m;
    }

    @Override
    public String toString() {
        return "Point[" + (category != null ? category : String.valueOf(x)) + ", " + y + (key != null ? ", key=" + key : "") + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Point)) {
            return false;
        }
        Point p = (Point) o;
        return p.x == x && p.y == y && eq(p.category, category) && eq(p.size, size) && eq(p.key, key) && eq(p.label, label)
                && eq(p.color, color) && eq(p.meta, meta);
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    @Override
    public int hashCode() {
        return Double.hashCode(x) * 31 + Double.hashCode(y) + (category == null ? 0 : category.hashCode());
    }

    static List<Point> list(List<Point> l) {
        return Collections.unmodifiableList(l);
    }
}
