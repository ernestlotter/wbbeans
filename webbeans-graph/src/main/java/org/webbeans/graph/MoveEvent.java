package org.webbeans.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.webbeans.ui.Component;
import org.webbeans.ui.Event;

/** The user dragged nodes or notes; the new locations are already applied to the widgets. */
public class MoveEvent extends Event {

    public MoveEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }

    public GraphScene<?, ?> getScene() {
        return (GraphScene<?, ?>) getSource();
    }

    /** The moved widgets ({@link NodeWidget}s and {@link NoteWidget}s). */
    @SuppressWarnings("unchecked")
    public List<Widget> getWidgets() {
        List<Widget> out = new ArrayList<Widget>();
        Object moves = getData().get("moves");
        if (moves instanceof Map) {
            for (String id : ((Map<String, Object>) moves).keySet()) {
                Widget w = getScene().widget(id);
                if (w != null) {
                    out.add(w);
                }
            }
        }
        return out;
    }
}
