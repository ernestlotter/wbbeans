package org.webbeans.graph;

import java.util.Map;

import org.webbeans.ui.Component;
import org.webbeans.ui.Event;

/** The user edited a label (node) or text (note) in place; nothing is applied until the listener does it. */
public class RenameEvent extends Event {

    public RenameEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }

    public GraphScene<?, ?> getScene() {
        return (GraphScene<?, ?>) getSource();
    }

    public Widget getWidget() {
        return getScene().widget(getString("id"));
    }

    public Object getObject() {
        Widget w = getWidget();
        return w == null ? null : w.getObject();
    }

    public String getText() {
        String t = getString("text");
        return t == null ? "" : t;
    }

    /** Applies the text to the widget (label of a node, text of a note). */
    public void accept() {
        Widget w = getWidget();
        if (w instanceof NodeWidget) {
            ((NodeWidget) w).setLabel(getText());
        } else if (w instanceof NoteWidget) {
            ((NoteWidget) w).setText(getText());
        }
    }
}
