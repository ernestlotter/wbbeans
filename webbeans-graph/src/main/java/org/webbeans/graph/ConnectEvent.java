package org.webbeans.graph;

import java.util.Map;

import org.webbeans.ui.Component;
import org.webbeans.ui.Event;

/** The user connected two widgets (fired after the {@link ConnectProvider} ran, when one is installed). */
public class ConnectEvent extends Event {

    public ConnectEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }

    public GraphScene<?, ?> getScene() {
        return (GraphScene<?, ?>) getSource();
    }

    public Widget getSourceWidget() {
        return getScene().widget(getString("source"));
    }

    public Widget getTargetWidget() {
        return getScene().widget(getString("target"));
    }

    /** The node object at the source end (the pin's node when the drag started on a pin). */
    public Object getSourceObject() {
        NodeWidget n = EdgeWidget.nodeOf(getSourceWidget());
        return n == null ? null : n.getObject();
    }

    public Object getTargetObject() {
        NodeWidget n = EdgeWidget.nodeOf(getTargetWidget());
        return n == null ? null : n.getObject();
    }
}
