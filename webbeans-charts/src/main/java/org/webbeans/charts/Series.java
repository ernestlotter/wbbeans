package org.webbeans.charts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One series of a {@link ChartModel}: style and points. Every mutation is one small delta to the browser
 * (batched per flush): {@link #setPoints} replaces, {@link #addPoint} appends (and trims to the
 * {@link #setWindow window}), {@link #updatePoint}/{@link #removePoint} work on keyed points.
 */
public final class Series {

    /** Receives the mutations (the model). */
    interface Sink {
        void styleChanged(Series s);

        void pointsSet(Series s);

        void pointsAppended(Series s, List<Point> added);

        void pointUpdated(Series s, Object key, Point p);

        void pointRemoved(Series s, Object key);
    }

    private final String id;
    private final Sink sink;
    private String displayName;
    private ChartType type;
    private String color;
    private int yAxis;
    private boolean visible = true;
    private LineStyle lineStyle = LineStyle.SOLID;
    private Marker marker = Marker.NONE;
    private boolean smooth;
    private String stack;
    private final List<Point> points = new ArrayList<Point>();
    private int window;

    Series(String id, String displayName, ChartType type, Sink sink) {
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        this.sink = sink;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Series setDisplayName(String name) {
        this.displayName = name;
        sink.styleChanged(this);
        return this;
    }

    /** The series type, or null for the chart's default. */
    public ChartType getType() {
        return type;
    }

    public Series setType(ChartType t) {
        this.type = t;
        sink.styleChanged(this);
        return this;
    }

    /** A CSS colour; null = the palette colour of the series' position. */
    public String getColor() {
        return color;
    }

    public Series setColor(String cssColor) {
        this.color = cssColor;
        sink.styleChanged(this);
        return this;
    }

    /** 0 = the left/primary value axis, 1 = the secondary one (created on demand). */
    public int getYAxis() {
        return yAxis;
    }

    public Series setYAxis(int axisIndex) {
        this.yAxis = axisIndex == 1 ? 1 : 0;
        sink.styleChanged(this);
        return this;
    }

    public boolean isVisible() {
        return visible;
    }

    public Series setVisible(boolean b) {
        this.visible = b;
        sink.styleChanged(this);
        return this;
    }

    /** Set by the client when the legend toggles the series (no delta back). */
    void setVisibleFromClient(boolean b) {
        this.visible = b;
    }

    public LineStyle getLineStyle() {
        return lineStyle;
    }

    public Series setLineStyle(LineStyle s) {
        this.lineStyle = s == null ? LineStyle.SOLID : s;
        sink.styleChanged(this);
        return this;
    }

    public Marker getMarker() {
        return marker;
    }

    public Series setMarker(Marker m) {
        this.marker = m == null ? Marker.NONE : m;
        sink.styleChanged(this);
        return this;
    }

    /** Monotone curve instead of straight segments (line/area). */
    public Series setSmooth(boolean b) {
        this.smooth = b;
        sink.styleChanged(this);
        return this;
    }

    public boolean isSmooth() {
        return smooth;
    }

    /** Stack group: series with the same group stack on each other (bars, areas); null = the chart's {@code stacked} setting. */
    public Series setStack(String group) {
        this.stack = group;
        sink.styleChanged(this);
        return this;
    }

    public String getStack() {
        return stack;
    }

    public List<Point> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public int getPointCount() {
        return points.size();
    }

    /** Replaces all points (one delta). */
    public Series setPoints(List<Point> newPoints) {
        points.clear();
        points.addAll(newPoints);
        trim();
        sink.pointsSet(this);
        return this;
    }

    public Series addPoint(Point p) {
        return addPoints(Collections.singletonList(p));
    }

    /** Appends; with a window the oldest points are dropped on both sides. */
    public Series addPoints(List<Point> added) {
        if (added.isEmpty()) {
            return this;
        }
        points.addAll(added);
        trim();
        sink.pointsAppended(this, new ArrayList<Point>(added));
        return this;
    }

    /** Replaces the point with that key (or appends the point, keyed, if absent). */
    public Series updatePoint(Object key, Point p) {
        Point keyed = p.getKey() != null && p.getKey().equals(key) ? p : p.withKey(key);
        int i = indexOfKey(key);
        if (i < 0) {
            points.add(keyed);
        } else {
            points.set(i, keyed);
        }
        sink.pointUpdated(this, key, keyed);
        return this;
    }

    public Series removePoint(Object key) {
        int i = indexOfKey(key);
        if (i >= 0) {
            points.remove(i);
            sink.pointRemoved(this, key);
        }
        return this;
    }

    private int indexOfKey(Object key) {
        for (int i = 0; i < points.size(); i++) {
            Object k = points.get(i).getKey();
            if (k != null && k.equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /** Sliding window: keeps at most {@code maxPoints} (0 = unbounded); the client drops the same points. */
    public Series setWindow(int maxPoints) {
        this.window = Math.max(0, maxPoints);
        if (trim()) {
            sink.pointsSet(this);
        }
        return this;
    }

    public int getWindow() {
        return window;
    }

    private boolean trim() {
        if (window > 0 && points.size() > window) {
            points.subList(0, points.size() - window).clear();
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "Series[" + id + ", " + points.size() + " points]";
    }
}
