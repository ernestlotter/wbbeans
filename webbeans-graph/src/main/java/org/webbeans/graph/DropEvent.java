package org.webbeans.graph;

import java.util.Map;

import org.webbeans.ui.Component;

/** A {@code Palette} item was dropped on the scene. */
public class DropEvent extends WidgetEvent {

    public DropEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }

    /** The palette item's key. */
    public String getItemKey() {
        return getString("item");
    }
}
