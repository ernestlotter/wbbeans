package org.webbeans.ui.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;
import org.webbeans.ui.Resource;

/**
 * A scrolling list of domain objects with single or multiple selection (client-owned, keys on the wire).
 * {@link SelectionEvent} on every change, {@link ActivateEvent} on double-click / Enter. For nodes with actions
 * and properties use the explorer's {@code ListView}.
 */
@Renderer(id = "wb.ListBox", resource = "org/webbeans/ui/client/renderers/listbox.js")
public class ListBox<T> extends Component {

    @SuppressWarnings("unchecked")
    public static final Property<List<Item>> ITEMS = (Property<List<Item>>) (Property<?>) Property.of("items", List.class, new ArrayList<Item>());
    @SuppressWarnings("unchecked")
    public static final Property<List<String>> SELECTED = (Property<List<String>>) (Property<?>) Property.of("selected", List.class, new ArrayList<String>()).clientOwned();
    public static final Property<Boolean> MULTI = Property.of("multi", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> CHECKBOXES = Property.of("checkboxes", Boolean.class, Boolean.FALSE);
    public static final Property<Integer> VISIBLE_ROWS = Property.of("visibleRows", Integer.class, null);
    public static final Property<String> EMPTY_TEXT = Property.of("emptyText", String.class, null);

    public static final EventType<SelectionEvent> SELECTION = EventType.of("select", SelectionEvent::new);
    public static final EventType<ActivateEvent> ACTIVATE = EventType.of("activate", ActivateEvent::new);

    /** {@code select {keys}}: the selection as the client sees it. */
    public static class SelectionEvent extends Event {
        public SelectionEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        @SuppressWarnings("unchecked")
        public List<String> getKeys() {
            Object v = getData().get("keys");
            return v instanceof List ? (List<String>) v : Collections.<String>emptyList();
        }

        /** The selected domain objects (resolved by the list). */
        @SuppressWarnings("unchecked")
        public <T> List<T> getSelected() {
            return ((ListBox<T>) getSource()).getSelected();
        }
    }

    /** {@code activate {key}}: double-click or Enter on a row. */
    public static class ActivateEvent extends Event {
        public ActivateEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getKey() {
            return getString("key");
        }

        @SuppressWarnings("unchecked")
        public <T> T getItem() {
            return ((ListBox<T>) getSource()).items.byKey(getKey());
        }
    }

    private final Items<T> items = new Items<T>();

    public ListBox() {
        super("wb.ListBox");
        addListener(SELECTION, new Listener<SelectionEvent>() {
            @Override
            public void onEvent(SelectionEvent e) {
                setFromClient(SELECTED, new ArrayList<String>(e.getKeys()));
            }
        });
    }

    public ListBox<T> setKeyFunction(Function<T, String> f) {
        items.setKeyFunction(f);
        return this;
    }

    public ListBox<T> setItems(Collection<? extends T> objects) {
        set(ITEMS, items.set(objects));
        // drop selected keys that are gone
        List<String> keep = new ArrayList<String>();
        for (String k : get(SELECTED)) {
            if (items.contains(k)) {
                keep.add(k);
            }
        }
        if (keep.size() != get(SELECTED).size()) {
            set(SELECTED, keep);
        }
        return this;
    }

    public ListBox<T> setItems(Collection<? extends T> objects, Function<T, String> label) {
        items.setLabelFunction(label);
        return setItems(objects);
    }

    public ListBox<T> setItems(Collection<? extends T> objects, Function<T, String> label, Function<T, Resource> icon) {
        items.setLabelFunction(label).setIconFunction(icon);
        return setItems(objects);
    }

    public ListBox<T> setDescriptions(Function<T, String> description) {
        items.setDescriptionFunction(description);
        return setItems(new ArrayList<T>(items.values()));
    }

    public List<T> getItems() {
        return new ArrayList<T>(items.values());
    }

    public ListBox<T> setMulti(boolean b) {
        set(MULTI, b);
        return this;
    }

    /** Show a check box per row (implies multi-selection by clicking rows without modifier keys). */
    public ListBox<T> setCheckboxes(boolean b) {
        set(CHECKBOXES, b);
        if (b) {
            set(MULTI, Boolean.TRUE);
        }
        return this;
    }

    /** Height as a number of rows (else the list fills its container). */
    public ListBox<T> setVisibleRows(Integer rows) {
        set(VISIBLE_ROWS, rows);
        return this;
    }

    public ListBox<T> setEmptyText(String t) {
        set(EMPTY_TEXT, t);
        return this;
    }

    public List<T> getSelected() {
        return items.objects(get(SELECTED));
    }

    public T getSelectedItem() {
        List<T> s = getSelected();
        return s.isEmpty() ? null : s.get(0);
    }

    public ListBox<T> setSelected(Collection<? extends T> objects) {
        set(SELECTED, items.keys(objects));
        return this;
    }

    public ListBox<T> setSelectedItem(T object) {
        return setSelected(object == null ? Collections.<T>emptyList() : Collections.singletonList(object));
    }

    public void scrollTo(T object) {
        String k = items.key(object);
        if (k != null) {
            call("scrollTo", k);
        }
    }

    public Registration addSelectionListener(Listener<SelectionEvent> l) {
        return addListener(SELECTION, l);
    }

    public Registration addActivateListener(Listener<ActivateEvent> l) {
        return addListener(ACTIVATE, l);
    }
}
