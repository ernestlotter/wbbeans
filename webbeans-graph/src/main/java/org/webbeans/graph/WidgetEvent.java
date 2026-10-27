package org.webbeans.graph;

import java.util.Map;

import org.webbeans.ui.Component;
import org.webbeans.ui.Event;

/** An event about one widget (activate, hover, context menu): the widget, its object and the pointer position. */
public class WidgetEvent extends Event {

    public WidgetEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }

    public GraphScene<?, ?> getScene() {
        return (GraphScene<?, ?>) getSource();
    }

    /** The widget, or null for the scene background (hover left, background menu). */
    public Widget getWidget() {
        return getScene().widget(getString("id"));
    }

    /** The node or edge object behind the widget, or null. */
    public Object getObject() {
        Widget w = getWidget();
        return w == null ? null : w.getObject();
    }

    /** Pointer position in page coordinates (for popups). */
    public int getX() {
        return getInt("x", 0);
    }

    public int getY() {
        return getInt("y", 0);
    }

    /** Pointer position in scene coordinates. */
    public double getSceneX() {
        return getDouble("sx", 0);
    }

    public double getSceneY() {
        return getDouble("sy", 0);
    }
}
