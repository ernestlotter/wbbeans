package org.webbeans.ui;

import java.util.Map;

/** A click on a component. */
public class ClickEvent extends Event {

    public ClickEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }

    /** 0 = left, 1 = middle, 2 = right. */
    public int getButton() {
        return getInt("button", 0);
    }

    public int getClickCount() {
        return getInt("count", 1);
    }

    public boolean isCtrl() {
        return getBoolean("ctrl");
    }

    public boolean isShift() {
        return getBoolean("shift");
    }

    public boolean isAlt() {
        return getBoolean("alt");
    }

    public boolean isMeta() {
        return getBoolean("meta");
    }
}
