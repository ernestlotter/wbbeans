package org.webbeans.ui.components;

import java.util.LinkedHashMap;
import java.util.Map;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Container;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;
import org.webbeans.ui.Resource;

/** Tabs; each child is a tab whose title/icon/closable come from its constraint. */
@Renderer(id = "wb.TabbedPane", resource = "org/webbeans/ui/client/renderers/tabbedpane.js")
public class TabbedPane extends Container {

    public static final Property<Integer> SELECTED = Property.of("selected", Integer.class, 0).clientOwned();
    public static final EventType<Event> SELECTION = EventType.of("select", Event::new);
    public static final EventType<Event> TAB_CLOSE = EventType.of("tabClose", Event::new);

    public TabbedPane() {
        super("wb.TabbedPane");
        addListener(SELECTION, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                setFromClient(SELECTED, e.getInt("index", 0));
            }
        });
    }

    public TabbedPane addTab(String title, Component content) {
        return addTab(title, null, content, false);
    }

    public TabbedPane addTab(String title, Resource icon, Component content, boolean closable) {
        Map<String, Object> c = new LinkedHashMap<String, Object>();
        c.put("title", title);
        if (icon != null) {
            c.put("icon", icon);
        }
        if (closable) {
            c.put("closable", Boolean.TRUE);
        }
        add(content, c);
        return this;
    }

    public int getSelectedIndex() {
        return get(SELECTED);
    }

    public TabbedPane setSelectedIndex(int i) {
        set(SELECTED, i);
        return this;
    }

    public Component getSelectedComponent() {
        int i = getSelectedIndex();
        return i >= 0 && i < getChildCount() ? getChildren().get(i) : null;
    }

    public Registration addSelectionListener(Listener<Event> l) {
        return addListener(SELECTION, l);
    }

    /** The event's {@code index} names the tab; remove the child to close it. */
    public Registration addTabCloseListener(Listener<Event> l) {
        return addListener(TAB_CLOSE, l);
    }
}
