package org.webbeans.ui.table;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Registration;

/**
 * A {@link Table} whose rows form a tree (NetBeans' OutlineView without nodes): the first column shows the
 * hierarchy with indentation and expanders, the other columns are ordinary {@link Column}s. The expanded part of
 * a {@link TreeTableModel} is flattened on the server, so paging, sorting (within siblings) and filtering
 * (matches are shown with their ancestors) work as in the flat table and the client only ever sees rows.
 */
@Renderer(id = "wb.TreeTable", resource = "org/webbeans/ui/client/renderers/treetable.js", css = "org/webbeans/ui/client/renderers/treetable.css")
public class TreeTable<T> extends Table<T> {

    /** A node was expanded or collapsed: {@link #getNode()}, {@link #isExpanded()}. */
    public static class ExpandEvent extends Event {
        public ExpandEvent(org.webbeans.ui.Component source, Map<String, Object> data) {
            super(source, data);
        }

        @SuppressWarnings("unchecked")
        public <N> N getNode() {
            return (N) ((TreeTable<?>) getSource()).row(getString("key"));
        }

        public boolean isExpanded() {
            return getBoolean("expanded");
        }
    }

    public static final EventType<Event> TOGGLE = EventType.of("toggle", Event::new);
    public static final EventType<ExpandEvent> EXPAND = EventType.of("expand", ExpandEvent::new);

    private static final class Info {
        final int depth;
        final boolean leaf;
        final String parent;

        Info(int depth, boolean leaf, String parent) {
            this.depth = depth;
            this.leaf = leaf;
            this.parent = parent;
        }
    }

    private TreeTableModel<T> treeModel;
    private final Set<String> expanded = new LinkedHashSet<String>();
    private final Map<String, Info> info = new HashMap<String, Info>();
    private final FlatModel flat = new FlatModel();
    private boolean filtering;

    public TreeTable() {
        super("wb.TreeTable");
        addListener(TOGGLE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                String k = e.getString("key");
                T node = row(k);
                if (node != null) {
                    if (expanded.contains(k)) {
                        collapse(node);
                    } else {
                        expand(node);
                    }
                }
            }
        });
    }

    public TreeTable(TreeTableModel<T> model) {
        this();
        setTreeModel(model);
    }

    public TreeTable<T> setTreeModel(final TreeTableModel<T> m) {
        this.treeModel = m;
        expanded.clear();
        info.clear();
        if (m != null) {
            setKeyFunction(new Function<T, String>() {
                @Override
                public String apply(T t) {
                    return m.getKey(t);
                }
            });
        }
        setModel(m == null ? null : flat);
        return this;
    }

    public TreeTableModel<T> getTreeModel() {
        return treeModel;
    }

    /** Convenience: roots plus a children function; leaves are nodes without children. */
    public TreeTable<T> setTree(final Collection<? extends T> roots, final Function<T, ? extends Collection<? extends T>> children) {
        return setTreeModel(new TreeTableModel<T>() {
            @Override
            public List<? extends T> getRoots() {
                return new ArrayList<T>(roots);
            }

            @Override
            public List<? extends T> getChildren(T parent) {
                Collection<? extends T> c = children.apply(parent);
                return c == null ? Collections.<T>emptyList() : new ArrayList<T>(c);
            }

            @Override
            public boolean isLeaf(T node) {
                Collection<? extends T> c = children.apply(node);
                return c == null || c.isEmpty();
            }
        });
    }

    // ---- expansion ----------------------------------------------------------------------------------------------

    public boolean isExpanded(T node) {
        return expanded.contains(key(node));
    }

    public TreeTable<T> expand(T node) {
        checkThread();
        String k = key(node);
        if (treeModel != null && !treeModel.isLeaf(node) && expanded.add(k)) {
            structureChanged();
            fire(k, true);
        }
        return this;
    }

    public TreeTable<T> collapse(T node) {
        checkThread();
        String k = key(node);
        if (expanded.remove(k)) {
            structureChanged();
            fire(k, false);
        }
        return this;
    }

    /** Expands the node and every ancestor so it is visible. */
    public TreeTable<T> expandPath(List<? extends T> path) {
        boolean changed = false;
        for (T n : path) {
            if (treeModel != null && !treeModel.isLeaf(n)) {
                changed |= expanded.add(key(n));
            }
        }
        if (changed) {
            structureChanged();
        }
        return this;
    }

    /** Expands every node (the whole tree is walked: fine for trees of hundreds, not of millions). */
    public TreeTable<T> expandAll() {
        if (treeModel != null) {
            for (T r : treeModel.getRoots()) {
                expandRecursively(r);
            }
            structureChanged();
        }
        return this;
    }

    private void expandRecursively(T n) {
        if (!treeModel.isLeaf(n)) {
            expanded.add(key(n));
            for (T c : treeModel.getChildren(n)) {
                expandRecursively(c);
            }
        }
    }

    public TreeTable<T> collapseAll() {
        if (!expanded.isEmpty()) {
            expanded.clear();
            structureChanged();
        }
        return this;
    }

    public Set<String> getExpandedKeys() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(expanded));
    }

    private void fire(String key, boolean open) {
        if (isListened(EXPAND.getName())) {
            Map<String, Object> d = new HashMap<String, Object>();
            d.put("key", key);
            d.put("expanded", open);
            fireEvent(EXPAND, d);
        }
    }

    /** The tree changed shape (children added or removed): re-flattens and re-sends the rows. */
    public void structureChanged() {
        flat.invalidate();
        flat.fireReset();
    }

    /** One node's values changed; re-renders its row. */
    public void nodeChanged(T node) {
        flat.invalidate();
        updateRow(node);
    }

    public int getDepth(T node) {
        Info i = info.get(key(node));
        return i == null ? 0 : i.depth;
    }

    @Override
    protected void decorateRow(T row, Map<String, Object> encoded) {
        Info i = info.get(key(row));
        if (i == null) {
            return;
        }
        encoded.put("d", i.depth);
        if (!i.leaf) {
            encoded.put("x", Boolean.TRUE);
            if (filtering || expanded.contains(key(row))) {
                encoded.put("e", Boolean.TRUE);
            }
        }
        if (i.parent != null) {
            encoded.put("p", i.parent);
        }
    }

    public Registration addExpandListener(Listener<ExpandEvent> l) {
        return addListener(EXPAND, l);
    }

    // ---- flattening ---------------------------------------------------------------------------------------------

    private final class FlatModel implements TableModel<T> {
        private final List<TableModel.Listener<T>> listeners = new ArrayList<TableModel.Listener<T>>();
        private List<T> cached;
        private String cachedFor;

        void invalidate() {
            cached = null;
        }

        void fireReset() {
            for (TableModel.Listener<T> l : new ArrayList<TableModel.Listener<T>>(listeners)) {
                l.reset();
            }
        }

        @Override
        public void load(Query<T> query, int offset, int limit, Callback<T> callback) {
            String sig = query.toString() + "|" + expanded.hashCode();
            if (cached == null || !sig.equals(cachedFor)) {
                cached = flatten(query);
                cachedFor = sig;
            }
            int from = Math.min(offset, cached.size());
            int to = Math.min(cached.size(), offset + limit);
            callback.loaded(cached.size(), from, new ArrayList<T>(cached.subList(from, to)));
        }

        private List<T> flatten(Query<T> query) {
            info.clear();
            List<T> out = new ArrayList<T>();
            filtering = query.getMatcher() != null;
            if (treeModel != null) {
                walk(treeModel.getRoots(), 0, null, query.getComparator(), query.getMatcher(), out);
            }
            return out;
        }

        private boolean walk(List<? extends T> nodes, int depth, String parent, Comparator<T> cmp, Predicate<T> matcher, List<T> out) {
            List<T> sorted = new ArrayList<T>(nodes);
            if (cmp != null) {
                Collections.sort(sorted, cmp);
            }
            boolean any = false;
            for (T n : sorted) {
                String k = key(n);
                boolean leaf = treeModel.isLeaf(n);
                if (matcher != null) {
                    // keep a node if it matches or one of its descendants does; show the matching subtree expanded
                    int mark = out.size();
                    out.add(n);
                    info.put(k, new Info(depth, leaf, parent));
                    boolean below = !leaf && walk(treeModel.getChildren(n), depth + 1, k, cmp, matcher, out);
                    if (!below && !matcher.test(n)) {
                        while (out.size() > mark) {
                            info.remove(key(out.remove(out.size() - 1)));
                        }
                    } else {
                        any = true;
                    }
                } else {
                    out.add(n);
                    info.put(k, new Info(depth, leaf, parent));
                    any = true;
                    if (!leaf && expanded.contains(k)) {
                        walk(treeModel.getChildren(n), depth + 1, k, cmp, null, out);
                    }
                }
            }
            return any;
        }

        @Override
        public void addListener(TableModel.Listener<T> l) {
            listeners.add(l);
        }

        @Override
        public void removeListener(TableModel.Listener<T> l) {
            listeners.remove(l);
        }
    }
}
