package org.webbeans.charts;

import java.util.Map;

import org.webbeans.ui.Component;
import org.webbeans.ui.Event;

/** A point was clicked: the series and the point (resolved from its key or index). */
public class PointEvent extends Event {

    public PointEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }

    public String getSeriesId() {
        return getString("series");
    }

    public Series getSeries() {
        return ((Chart) getSource()).getModel().getSeries(getSeriesId());
    }

    /** The point, or null if it is gone meanwhile. */
    public Point getPoint() {
        Series s = getSeries();
        if (s == null) {
            return null;
        }
        String key = getString("k");
        if (key != null) {
            for (Point p : s.getPoints()) {
                if (p.getKey() != null && key.equals(String.valueOf(p.getKey()))) {
                    return p;
                }
            }
        }
        int index = getInt("index", -1);
        return index >= 0 && index < s.getPointCount() ? s.getPoints().get(index) : null;
    }

    public int getIndex() {
        return getInt("index", -1);
    }

    public double getX() {
        return getDouble("x", Double.NaN);
    }

    public double getY() {
        return getDouble("y", Double.NaN);
    }

    public boolean isCtrl() {
        return getBoolean("ctrl");
    }

    public boolean isShift() {
        return getBoolean("shift");
    }
}
