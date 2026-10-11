package org.webbeans.charts;

import java.util.LinkedHashMap;
import java.util.Map;

/** The legend: where it sits, whether it is shown, whether clicking an entry toggles the series. */
public final class Legend {

    public enum Position { TOP, BOTTOM, LEFT, RIGHT }

    private final Runnable changed;
    private Position position = Position.BOTTOM;
    private boolean visible = true;
    private boolean interactive = true;

    Legend(Runnable changed) {
        this.changed = changed;
    }

    public Position getPosition() {
        return position;
    }

    public Legend setPosition(Position p) {
        position = p == null ? Position.BOTTOM : p;
        changed.run();
        return this;
    }

    public boolean isVisible() {
        return visible;
    }

    public Legend setVisible(boolean b) {
        visible = b;
        changed.run();
        return this;
    }

    public boolean isInteractive() {
        return interactive;
    }

    public Legend setInteractive(boolean b) {
        interactive = b;
        changed.run();
        return this;
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("position", position.name().toLowerCase(java.util.Locale.ROOT));
        m.put("visible", visible);
        m.put("interactive", interactive);
        return m;
    }
}
