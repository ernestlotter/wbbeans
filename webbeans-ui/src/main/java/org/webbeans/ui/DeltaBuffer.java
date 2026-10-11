package org.webbeans.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the mutations of one UI between flushes and merges them:
 * {@code set} after {@code create} folds into the create; {@code create} followed by {@code remove} cancels both;
 * repeated {@code set}s keep the last value; {@code call}s keep their relative order.
 */
public final class DeltaBuffer {

    private final List<Map<String, Object>> ops = new ArrayList<Map<String, Object>>();
    private final Map<Integer, Map<String, Object>> created = new HashMap<Integer, Map<String, Object>>();

    public boolean isEmpty() {
        return ops.isEmpty();
    }

    void create(Component c, Component parent, int index) {
        Map<String, Object> op = new LinkedHashMap<String, Object>();
        op.put("op", "create");
        op.put("id", c.getId());
        op.put("type", c.getRendererId());
        op.put("parent", parent != null ? parent.getId() : null);
        op.put("index", index);
        if (c.getConstraint() != null) {
            op.put("c", JsonCodec.encode(c.getConstraint()));
        }
        op.put("props", JsonCodec.encodeProps(c.propertyValues()));
        List<Object> events = new ArrayList<Object>();
        for (EventType<?> t : c.listenedEvents()) {
            events.add(eventSpec(t));
        }
        op.put("events", events);
        ops.add(op);
        created.put(c.getId(), op);
    }

    private static Object eventSpec(EventType<?> t) {
        if (t.getDebounceMs() <= 0) {
            return t.getName();
        }
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("type", t.getName());
        m.put("lazy", t.getDebounceMs());
        return m;
    }

    @SuppressWarnings("unchecked")
    void set(Component c, String prop, Object value) {
        Object encoded = JsonCodec.encode(value);
        Map<String, Object> cr = created.get(c.getId());
        if (cr != null) {
            ((Map<String, Object>) cr.get("props")).put(prop, encoded);
            return;
        }
        if (!ops.isEmpty()) {
            Map<String, Object> last = ops.get(ops.size() - 1);
            if ("set".equals(last.get("op")) && last.get("id").equals(c.getId())) {
                ((Map<String, Object>) last.get("props")).put(prop, encoded);
                return;
            }
        }
        Map<String, Object> op = new LinkedHashMap<String, Object>();
        op.put("op", "set");
        op.put("id", c.getId());
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put(prop, encoded);
        op.put("props", props);
        ops.add(op);
    }

    void remove(Component c) {
        if (created.containsKey(c.getId())) {
            // never sent: drop everything about it and its subtree
            List<Integer> ids = new ArrayList<Integer>();
            ids.add(c.getId());
            if (c instanceof Container) {
                List<Component> all = new ArrayList<Component>();
                ((Container) c).walk(all);
                for (Component x : all) {
                    ids.add(x.getId());
                }
            }
            Iterator<Map<String, Object>> it = ops.iterator();
            while (it.hasNext()) {
                Map<String, Object> op = it.next();
                if (ids.contains(op.get("id"))) {
                    it.remove();
                }
            }
            for (Integer id : ids) {
                created.remove(id);
            }
            return;
        }
        Map<String, Object> op = new LinkedHashMap<String, Object>();
        op.put("op", "remove");
        op.put("id", c.getId());
        ops.add(op);
    }

    void call(Component c, String method, Object[] args) {
        Map<String, Object> op = new LinkedHashMap<String, Object>();
        op.put("op", "call");
        op.put("id", c.getId());
        op.put("m", method);
        op.put("args", JsonCodec.encode(args == null ? new Object[0] : Arrays.asList(args)));
        ops.add(op);
    }

    @SuppressWarnings("unchecked")
    void listen(Component c, EventType<?> type) {
        Map<String, Object> cr = created.get(c.getId());
        if (cr != null) {
            ((List<Object>) cr.get("events")).add(eventSpec(type));
            return;
        }
        Map<String, Object> op = new LinkedHashMap<String, Object>();
        op.put("op", "listen");
        op.put("id", c.getId());
        op.put("events", new ArrayList<Object>(Arrays.asList(eventSpec(type))));
        ops.add(op);
    }

    /** Returns and clears the collected ops. */
    List<Map<String, Object>> drain() {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>(ops);
        ops.clear();
        created.clear();
        return out;
    }

    void clear() {
        ops.clear();
        created.clear();
    }
}
