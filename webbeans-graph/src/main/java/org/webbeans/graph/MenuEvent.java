package org.webbeans.graph;

import java.util.Map;

import org.webbeans.ui.Component;

/** A right-click on a widget or on the background ({@link #getWidget()} null). */
public class MenuEvent extends WidgetEvent {

    public MenuEvent(Component source, Map<String, Object> data) {
        super(source, data);
    }
}
