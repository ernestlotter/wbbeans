package org.webbeans.charts;

import java.util.Map;

import org.webbeans.ui.Component;
import org.webbeans.ui.Event;

/** The user toggled a series in the legend; {@link Series#isVisible} is already updated. */
public class LegendEvent extends Event {

    public LegendEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }

    public String getSeriesId() {
        return getString("series");
    }

    public Series getSeries() {
        return ((Chart) getSource()).getModel().getSeries(getSeriesId());
    }

    public boolean isVisible() {
        return getBoolean("visible");
    }
}
