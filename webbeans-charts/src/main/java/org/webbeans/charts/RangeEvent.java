package org.webbeans.charts;

import java.util.Map;

import org.webbeans.ui.Component;
import org.webbeans.ui.Event;

/** The visible range changed (zoom, pan, brush); {@code NaN} for an axis that was not affected. Reset sends all NaN. */
public class RangeEvent extends Event {

    public RangeEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }

    public double getXFrom() {
        return getDouble("xFrom", Double.NaN);
    }

    public double getXTo() {
        return getDouble("xTo", Double.NaN);
    }

    public double getYFrom() {
        return getDouble("yFrom", Double.NaN);
    }

    public double getYTo() {
        return getDouble("yTo", Double.NaN);
    }

    /** True when the user reset the zoom (range = everything). */
    public boolean isReset() {
        return getBoolean("reset");
    }
}
