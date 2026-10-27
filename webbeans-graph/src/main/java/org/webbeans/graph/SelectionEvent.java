package org.webbeans.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.webbeans.ui.Component;
import org.webbeans.ui.Event;

/** The selection changed (by the user or by {@link GraphScene#setSelectedObjects}). */
public class SelectionEvent extends Event {

    public SelectionEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }

    public GraphScene<?, ?> getScene() {
        return (GraphScene<?, ?>) getSource();
    }

    /** The selected widgets (nodes and edges). */
    public List<Widget> getWidgets() {
        List<Widget> out = new ArrayList<Widget>();
        for (Widget w : getScene().getSelectedWidgets()) {
            out.add(w);
        }
        return out;
    }

    /** The selected node and edge objects. */
    public Set<Object> getObjects() {
        return getScene().getSelectedObjects();
    }
}
