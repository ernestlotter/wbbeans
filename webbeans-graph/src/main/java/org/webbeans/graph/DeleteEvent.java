package org.webbeans.graph;

import java.util.Map;

import org.webbeans.ui.Component;

/** The Delete key was pressed with a selection; nothing is removed unless a listener does it. */
public class DeleteEvent extends SelectionEvent {

    public DeleteEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }
}
