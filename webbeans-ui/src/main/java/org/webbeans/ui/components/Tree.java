package org.webbeans.ui.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * A plain tree over domain objects driven by a {@link TreeModel}: children are fetched when a node is first
 * expanded and pushed with a {@code children} call, so only the visited part of a large tree is ever sent.
 * Expansion and selection are client-owned (keys on the wire). For nodes with actions, properties, drag and
 * drop and popup menus use the explorer's {@code TreeView} over the Nodes API; this one is for simple pickers.
 */
@Renderer(id = "wb.Tree", resource = "org/webbeans/ui/client/renderers/tree.js")
public class Tree<T> extends Component {

    @SuppressWarnings("unchecked")
    public static final Property<List<Map<String, Object>>> ROOTS = (Property<List<Map<String, Object>>>) (Property<?>) Property.of("roots", List.class, new ArrayList<Object>());
    @SuppressWarnings("unchecked")
    public static final Property<List<String>> EXPANDED = (Property<List<String>>) (Property<?>) Property.of("expanded", List.class, new ArrayList<String>()).clientOwned();
    @SuppressWarnings("unchecked")
    public static final Property<List<String>> SELECTED = (Property<List<String>>) (Property<?>) Property.of("selected", List.class, new ArrayList<String>()).clientOwned();
    public static final Property<Boolean> MULTI = Property.of("multi", Boolean.class, Boolean.FALSE);
    public static final Property<String> EMPTY_TEXT = Property.of("emptyText", String.class, null);

    public static final EventType<ExpandEvent> EXPAND = EventType.of("expand", ExpandEvent::new);
    public static final EventType<SelectionEvent> SELECTION = EventType.of("select", SelectionEvent::new);
    public static final EventType<ActivateEvent> ACTIVATE = EventType.of("activate", ActivateEvent::new);

    public static class ExpandEvent extends Event {
        public ExpandEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getKey() {
            return getString("key");
        }

        public boolean isExpanded() {
            return getBoolean("expanded");
        }

        @SuppressWarnings("unchecked")
        public <T> T getNode() {
            return ((Tree<T>) getSource()).byKey.get(getKey());
        }
    }

    public static class SelectionEvent extends Event {
        public SelectionEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        @SuppressWarnings("unchecked")
        public List<String> getKeys() {
            Object v = getData().get("keys");
            return v instanceof List ? (List<String>) v : Collections.<String>emptyList();
        }

        @SuppressWarnings("unchecked")
        public <T> List<T> getSelected() {
            return ((Tree<T>) getSource()).getSelected();
        }
    }

    public static class ActivateEvent extends Event {
        public ActivateEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getKey() {
            return getString("key");
        }

        @SuppressWarnings("unchecked")
        public <T> T getNode() {
            return ((Tree<T>) getSource()).byKey.get(getKey());
        }
    }

    private TreeModel<T> model;
    private final Map<String, T> byKey = new LinkedHashMap<String, T>();
    private final Set<String> loaded = new LinkedHashSet<String>();

    public Tree() {
        super("wb.Tree");
        addListener(EXPAND, new Listener<ExpandEvent>() {
            @Override
            public void onEvent(ExpandEvent e) {
                List<String> exp = new ArrayList<String>(get(EXPANDED));
                if (e.isExpanded()) {
                    if (!exp.contains(e.getKey())) {
                        exp.add(e.getKey());
                    }
                    sendChildren(e.getKey(), false);
                } else {
                    exp.remove(e.getKey());
                }
                setFromClient(EXPANDED, exp);
            }
        });
        addListener(SELECTION, new Listener<SelectionEvent>() {
            @Override
            public void onEvent(SelectionEvent e) {
                setFromClient(SELECTED, new ArrayList<String>(e.getKeys()));
            }
        });
    }

    public Tree(TreeModel<T> model) {
        this();
        setModel(model);
    }

    /** A tree from two functions: the children of a node ({@code null} for the roots) and its label. */
    public static <T> Tree<T> of(final Function<T, List<T>> children, final Function<T, String> label) {
        return new Tree<T>(new TreeModel<T>() {
            @Override
            public List<T> getChildren(T parent) {
                List<T> l = children.apply(parent);
                return l == null ? Collections.<T>emptyList() : l;
            }

            @Override
            public String getLabel(T node) {
                return label.apply(node);
            }
        });
    }

    public Tree<T> setModel(TreeModel<T> model) {
        this.model = model;
        refresh();
        return this;
    }

    public TreeModel<T> getModel() {
        return model;
    }

    /** Re-reads the roots and the children of every expanded node from the model. */
    public void refresh() {
        byKey.clear();
        loaded.clear();
        if (model == null) {
            set(ROOTS, new ArrayList<Map<String, Object>>());
            return;
        }
        set(ROOTS, encode(model.getChildren(null)));
        for (String k : new ArrayList<String>(get(EXPANDED))) {
            if (byKey.containsKey(k)) {
                sendChildren(k, true);
            }
        }
        // whatever expanded/selected keys no longer exist are dropped by the client on render
    }

    /** Re-reads the children of one node (after the application changed them). */
    public void refresh(T node) {
        String k = key(node);
        if (k != null && byKey.containsKey(k)) {
            sendChildren(k, true);
        }
    }

    private void sendChildren(String key, boolean force) {
        T parent = byKey.get(key);
        if (parent == null || model == null || (loaded.contains(key) && !force)) {
            return;
        }
        loaded.add(key);
        call("children", key, encode(model.getChildren(parent)));
    }

    private List<Map<String, Object>> encode(List<T> nodes) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>(nodes.size());
        for (T n : nodes) {
            String k = model.getKey(n);
            byKey.put(k, n);
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("k", k);
            m.put("l", model.getLabel(n));
            Resource icon = model.getIcon(n);
            if (icon != null) {
                m.put("i", icon);
            }
            if (model.isLeaf(n)) {
                m.put("leaf", Boolean.TRUE);
            }
            out.add(m);
        }
        return out;
    }

    /** After a full {@code init} the client has the roots only: re-send the children of the expanded nodes. */
    @Override
    protected void resync() {
        for (String k : new ArrayList<String>(get(EXPANDED))) {
            if (byKey.containsKey(k)) {
                sendChildren(k, true);
            }
        }
    }

    public String key(T node) {
        return node == null || model == null ? null : model.getKey(node);
    }

    public T node(String key) {
        return byKey.get(key);
    }

    public Tree<T> setMulti(boolean b) {
        set(MULTI, b);
        return this;
    }

    public Tree<T> setEmptyText(String t) {
        set(EMPTY_TEXT, t);
        return this;
    }

    public List<T> getSelected() {
        List<T> out = new ArrayList<T>();
        for (String k : get(SELECTED)) {
            T t = byKey.get(k);
            if (t != null) {
                out.add(t);
            }
        }
        return out;
    }

    public T getSelectedNode() {
        List<T> s = getSelected();
        return s.isEmpty() ? null : s.get(0);
    }

    /** Selects nodes (they must have been sent, i.e. be roots or children of expanded nodes). */
    public Tree<T> setSelected(Collection<? extends T> nodes) {
        List<String> keys = new ArrayList<String>();
        for (T n : nodes) {
            keys.add(key(n));
        }
        set(SELECTED, keys);
        return this;
    }

    public Tree<T> setSelectedNode(T node) {
        return setSelected(node == null ? Collections.<T>emptyList() : Collections.singletonList(node));
    }

    /** Expands a node (fetching its children) from the server. */
    public Tree<T> expand(T node) {
        String k = key(node);
        if (k != null && byKey.containsKey(k)) {
            List<String> exp = new ArrayList<String>(get(EXPANDED));
            if (!exp.contains(k)) {
                exp.add(k);
                set(EXPANDED, exp);
            }
            sendChildren(k, false);
        }
        return this;
    }

    public Tree<T> collapse(T node) {
        String k = key(node);
        List<String> exp = new ArrayList<String>(get(EXPANDED));
        if (exp.remove(k)) {
            set(EXPANDED, exp);
        }
        return this;
    }

    public boolean isExpanded(T node) {
        return get(EXPANDED).contains(key(node));
    }

    public Registration addExpandListener(Listener<ExpandEvent> l) {
        return addListener(EXPAND, l);
    }

    public Registration addSelectionListener(Listener<SelectionEvent> l) {
        return addListener(SELECTION, l);
    }

    public Registration addActivateListener(Listener<ActivateEvent> l) {
        return addListener(ACTIVATE, l);
    }
}
