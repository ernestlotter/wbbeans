package org.webbeans.ui.components;

import java.util.ArrayList;
import java.util.Collection;
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

/** A path of clickable crumbs ("Projects › Acme › Orders"); the last one is the current location. */
@Renderer(id = "wb.Breadcrumb", resource = "org/webbeans/ui/client/renderers/widgets.js", css = "org/webbeans/ui/client/renderers/widgets.css")
public class Breadcrumb extends Component {

    /** A crumb was clicked: {@link #getKey()} / {@link #getIndex()}. */
    public static class SelectEvent extends Event {
        public SelectEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getKey() {
            return getString("key");
        }

        public int getIndex() {
            return getInt("index", -1);
        }
    }

    @SuppressWarnings("unchecked")
    public static final Property<List<Item>> ITEMS = (Property<List<Item>>) (Property<?>) Property.of("items", List.class, new ArrayList<Item>());
    public static final Property<String> SEPARATOR = Property.of("separator", String.class, "›");
    /** Collapse middle crumbs into "…" beyond this many. */
    public static final Property<Integer> MAX_ITEMS = Property.of("maxItems", Integer.class, null);

    public static final EventType<SelectEvent> SELECT = EventType.of("select", SelectEvent::new);

    private final List<Item> items = new ArrayList<Item>();

    public Breadcrumb() {
        super("wb.Breadcrumb");
    }

    public Breadcrumb add(String key, String label) {
        return add(key, label, null);
    }

    public Breadcrumb add(String key, String label, Resource icon) {
        items.add(new Item(key, label, icon, null, true));
        set(ITEMS, new ArrayList<Item>(items));
        return this;
    }

    public Breadcrumb setPath(Collection<Item> path) {
        items.clear();
        items.addAll(path);
        set(ITEMS, new ArrayList<Item>(items));
        return this;
    }

    /** Keeps the crumbs up to and including {@code key}. */
    public Breadcrumb truncateTo(String key) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getKey().equals(key)) {
                items.subList(i + 1, items.size()).clear();
                set(ITEMS, new ArrayList<Item>(items));
                break;
            }
        }
        return this;
    }

    public Breadcrumb clear() {
        items.clear();
        set(ITEMS, new ArrayList<Item>());
        return this;
    }

    public List<Item> getItems() {
        return new ArrayList<Item>(items);
    }

    public Breadcrumb setSeparator(String s) {
        set(SEPARATOR, s);
        return this;
    }

    public Breadcrumb setMaxItems(Integer n) {
        set(MAX_ITEMS, n);
        return this;
    }

    public Registration addSelectListener(Listener<SelectEvent> l) {
        return addListener(SELECT, l);
    }
}
