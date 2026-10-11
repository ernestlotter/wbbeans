package org.webbeans.ui.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
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

/**
 * Stacked collapsible sections, each a child component under a clickable header. One section open at a time
 * unless {@link #setMultiple}; which are open is client-owned ({@link #EXPANDED}, by index).
 */
@Renderer(id = "wb.Accordion", resource = "org/webbeans/ui/client/renderers/widgets.js", css = "org/webbeans/ui/client/renderers/widgets.css")
public class Accordion extends Container {

    /** The header of one section (the child's constraint). */
    public static final class Section implements org.webbeans.core.json.JsonValue {
        final String title;
        final Resource icon;
        final String badge;

        public Section(String title) {
            this(title, null, null);
        }

        public Section(String title, Resource icon, String badge) {
            this.title = title;
            this.icon = icon;
            this.badge = badge;
        }

        @Override
        public Object toJson() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("title", title);
            if (icon != null) {
                m.put("icon", icon);
            }
            if (badge != null) {
                m.put("badge", badge);
            }
            return m;
        }
    }

    @SuppressWarnings("unchecked")
    public static final Property<List<Integer>> EXPANDED = (Property<List<Integer>>) (Property<?>) Property.of("expanded", List.class, new ArrayList<Integer>()).clientOwned();
    public static final Property<Boolean> MULTIPLE = Property.of("multiple", Boolean.class, Boolean.FALSE);

    public static final EventType<Event> TOGGLE = EventType.of("toggle", Event::new);

    public Accordion() {
        super("wb.Accordion");
        addListener(TOGGLE, new Listener<Event>() {
            @Override
            @SuppressWarnings("unchecked")
            public void onEvent(Event e) {
                Object v = e.getData().get("expanded");
                List<Integer> l = new ArrayList<Integer>();
                if (v instanceof Collection) {
                    for (Object o : (Collection<?>) v) {
                        l.add(((Number) o).intValue());
                    }
                }
                setFromClient(EXPANDED, l);
            }
        });
    }

    /** Adds a section; the first one added is open. */
    public Accordion addSection(String title, Component content) {
        return addSection(new Section(title), content);
    }

    public Accordion addSection(Section header, Component content) {
        add(content, header);
        if (getChildCount() == 1 && get(EXPANDED).isEmpty()) {
            set(EXPANDED, new ArrayList<Integer>(java.util.Collections.singletonList(0)));
        }
        return this;
    }

    public Accordion setMultiple(boolean b) {
        set(MULTIPLE, b);
        return this;
    }

    public Accordion setExpanded(int... indexes) {
        List<Integer> l = new ArrayList<Integer>();
        for (int i : indexes) {
            l.add(i);
        }
        set(EXPANDED, l);
        return this;
    }

    public List<Integer> getExpanded() {
        return get(EXPANDED);
    }

    public boolean isExpanded(int index) {
        return get(EXPANDED).contains(index);
    }

    /** Fires after the user opened or closed a section ({@link #getExpanded()} is current). */
    public Registration addToggleListener(Listener<Event> l) {
        return addListener(TOGGLE, l);
    }
}
