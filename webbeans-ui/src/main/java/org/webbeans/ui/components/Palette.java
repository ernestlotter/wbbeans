package org.webbeans.ui.components;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;
import org.webbeans.ui.Resource;

/**
 * A palette of draggable items in collapsible categories (NetBeans' Palette window): the source for drops onto
 * a {@link Diagram} (which reports {@link Diagram#DROP} with the item key). Clicking an item also fires
 * {@link #SELECT}, for toolbars that insert on click.
 */
@Renderer(id = "wb.Palette", resource = "org/webbeans/ui/client/renderers/diagram.js", css = "org/webbeans/ui/client/renderers/diagram.css")
public class Palette extends Component {

    /** An item was clicked: {@link #getKey()}. */
    public static class SelectEvent extends Event {
        public SelectEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getKey() {
            return getString("key");
        }
    }

    @SuppressWarnings("unchecked")
    public static final Property<List<Map<String, Object>>> CATEGORIES = (Property<List<Map<String, Object>>>) (Property<?>) Property.of("categories", List.class, new ArrayList<Object>());
    public static final Property<Boolean> SHOW_LABELS = Property.of("showLabels", Boolean.class, Boolean.TRUE);
    public static final Property<String> FILTER = Property.of("filter", String.class, null).clientOwned();
    public static final Property<Boolean> SHOW_FILTER = Property.of("showFilter", Boolean.class, Boolean.FALSE);

    public static final EventType<SelectEvent> SELECT = EventType.of("select", SelectEvent::new);
    static final EventType<Event> FILTER_CHANGE = EventType.of("filter", Event::new).lazy(200);

    private final Map<String, List<Item>> categories = new LinkedHashMap<String, List<Item>>();

    public Palette() {
        super("wb.Palette");
        addListener(FILTER_CHANGE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                String t = e.getString("text");
                setFromClient(FILTER, t == null || t.isEmpty() ? null : t);
            }
        });
    }

    public Palette addItem(String category, String key, String label, Resource icon) {
        return addItem(category, key, label, icon, null);
    }

    public Palette addItem(String category, String key, String label, Resource icon, String description) {
        List<Item> l = categories.get(category);
        if (l == null) {
            l = new ArrayList<Item>();
            categories.put(category, l);
        }
        l.add(new Item(key, label, icon, description, true));
        publish();
        return this;
    }

    public Palette removeItem(String key) {
        for (List<Item> l : categories.values()) {
            for (int i = 0; i < l.size(); i++) {
                if (l.get(i).getKey().equals(key)) {
                    l.remove(i);
                    publish();
                    return this;
                }
            }
        }
        return this;
    }

    public Palette clear() {
        categories.clear();
        publish();
        return this;
    }

    public Palette setShowLabels(boolean b) {
        set(SHOW_LABELS, b);
        return this;
    }

    public Palette setShowFilter(boolean b) {
        set(SHOW_FILTER, b);
        return this;
    }

    private void publish() {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, List<Item>> e : categories.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("name", e.getKey());
            m.put("items", new ArrayList<Item>(e.getValue()));
            out.add(m);
        }
        set(CATEGORIES, out);
    }

    public Registration addSelectListener(Listener<SelectEvent> l) {
        return addListener(SELECT, l);
    }
}
