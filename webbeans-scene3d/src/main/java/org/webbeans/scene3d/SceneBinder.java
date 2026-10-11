package org.webbeans.scene3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Keeps a group of nodes in sync with a collection of domain objects, like {@code Children.Keys} does for
 * nodes: {@link #setItems} diffs by key, creates nodes for new items, updates existing ones and removes the
 * rest, so a model refresh costs only the ops for what actually changed. Created nodes get the item as user
 * data, so picks resolve back to it.
 *
 * <pre>
 * SceneBinder&lt;Planet&gt; planets = new SceneBinder&lt;Planet&gt;(scene.getRoot(), Planet::getName,
 *     p -&gt; new Mesh(Geometry.sphere(p.getRadius()), materialFor(p)),
 *     (p, n) -&gt; n.setPosition(p.getPosition()));
 * planets.setItems(solarSystem.getPlanets());
 * </pre>
 */
public final class SceneBinder<T> {

    private final Group parent;
    private final Function<? super T, ?> key;
    private final Function<? super T, ? extends Node3D> create;
    private final BiConsumer<? super T, ? super Node3D> update;
    private final Map<Object, Node3D> byKey = new LinkedHashMap<Object, Node3D>();
    private final Map<Object, T> items = new LinkedHashMap<Object, T>();

    /**
     * @param parent where the nodes live
     * @param key identity of an item (stable across refreshes); null for the item itself
     * @param create builds the node of a new item (its user data is set to the item afterwards)
     * @param update applies an item's current state to its node; also run right after creation. May be null.
     */
    public SceneBinder(Group parent, Function<? super T, ?> key, Function<? super T, ? extends Node3D> create,
            BiConsumer<? super T, ? super Node3D> update) {
        this.parent = parent;
        this.key = key == null ? Function.identity() : key;
        this.create = create;
        this.update = update;
    }

    /** Diffs against the current items: adds, updates and removes nodes as needed. */
    public void setItems(Collection<? extends T> newItems) {
        Map<Object, T> next = new LinkedHashMap<Object, T>();
        for (T t : newItems) {
            next.put(key.apply(t), t);
        }
        for (Object k : new ArrayList<Object>(byKey.keySet())) {
            if (!next.containsKey(k)) {
                Node3D n = byKey.remove(k);
                items.remove(k);
                parent.remove(n);
            }
        }
        for (Map.Entry<Object, T> e : next.entrySet()) {
            Node3D n = byKey.get(e.getKey());
            if (n == null) {
                n = create.apply(e.getValue());
                n.setUserData(e.getValue());
                byKey.put(e.getKey(), n);
                parent.add(n);
            } else {
                n.setUserData(e.getValue());
            }
            items.put(e.getKey(), e.getValue());
            if (update != null) {
                update.accept(e.getValue(), n);
            }
        }
    }

    /** Re-runs the update function for one item (after it changed in place). */
    public void refresh(T item) {
        Node3D n = byKey.get(key.apply(item));
        if (n != null && update != null) {
            update.accept(item, n);
        }
    }

    /** Re-runs the update function for every bound item. */
    public void refreshAll() {
        if (update != null) {
            for (Map.Entry<Object, T> e : items.entrySet()) {
                update.accept(e.getValue(), byKey.get(e.getKey()));
            }
        }
    }

    public Node3D nodeFor(T item) {
        return item == null ? null : byKey.get(key.apply(item));
    }

    @SuppressWarnings("unchecked")
    public T itemFor(Node3D n) {
        for (Map.Entry<Object, Node3D> e : byKey.entrySet()) {
            if (e.getValue() == n) {
                return items.get(e.getKey());
            }
        }
        return null;
    }

    public List<Node3D> getNodes() {
        return Collections.unmodifiableList(new ArrayList<Node3D>(byKey.values()));
    }

    public int size() {
        return byKey.size();
    }

    /** Removes every bound node. */
    public void clear() {
        setItems(Collections.<T>emptyList());
    }
}
