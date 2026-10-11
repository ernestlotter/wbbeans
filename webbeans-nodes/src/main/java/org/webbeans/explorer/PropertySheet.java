package org.webbeans.explorer;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.registry.Renderer;
import org.webbeans.nodes.Node;
import org.webbeans.nodes.NodeAdapter;
import org.webbeans.nodes.NodeListener;
import org.webbeans.nodes.PropertyEditor;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Resource;

/**
 * The property sets of the selected nodes as collapsible groups with in-place editors. Follows the nearest
 * {@link ExplorerManager}'s selection, or the nodes given with {@link #setNodes}. With several nodes it shows the
 * properties they have in common and writes a new value to all of them.
 */
@Renderer(id = "wb.PropertySheet", resource = "org/webbeans/nodes/client/propertysheet.js")
public class PropertySheet extends Component {

    public static final Property<Boolean> SHOW_DESCRIPTION = Property.of("showDescription", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> EXPERT_VISIBLE = Property.of("expertVisible", Boolean.class, Boolean.FALSE);
    public static final Property<Map<String, Object>> LABELS = Property.of("labels", mapClass(), null);
    /** {prop, text}: a cell was edited. */
    public static final EventType<Event> EDIT = EventType.of("edit", Event::new);

    private ExplorerManager explicitManager;
    private ExplorerManager em;
    private Node[] nodes = new Node[0];
    private boolean explicitNodes;
    private final PropertyChangeListener emListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (ExplorerManager.PROP_SELECTED_NODES.equals(evt.getPropertyName()) && !explicitNodes) {
                setNodesInternal(em.getSelectedNodes());
            }
        }
    };
    private final NodeListener nodeListener = new NodeAdapter() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            push();
        }

        @Override
        public void nodeDestroyed(org.webbeans.nodes.NodeEvent ev) {
            List<Node> keep = new ArrayList<Node>(Arrays.asList(nodes));
            keep.remove(ev.getNode());
            setNodesInternal(keep.toArray(new Node[keep.size()]));
        }
    };

    public PropertySheet() {
        super("wb.PropertySheet");
        Map<String, Object> l = new LinkedHashMap<String, Object>();
        l.put("noSelection", Bundles.getMessage(Node.class, "LBL_NoSelection"));
        l.put("noProperties", Bundles.getMessage(Node.class, "LBL_NoProperties"));
        set(LABELS, l);
        addListener(EDIT, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                edit(e.getString("prop"), e.getString("text"));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<String, Object>> mapClass() {
        return (Class<Map<String, Object>>) (Class<?>) Map.class;
    }

    public PropertySheet setExplorerManager(ExplorerManager m) {
        explicitManager = m;
        if (isAttached()) {
            bind();
        }
        return this;
    }

    /** Shows these nodes instead of a manager's selection (e.g. the global context). */
    public PropertySheet setNodes(Node... ns) {
        explicitNodes = true;
        setNodesInternal(ns == null ? new Node[0] : ns);
        return this;
    }

    public Node[] getNodes() {
        return nodes.clone();
    }

    public PropertySheet setExpertVisible(boolean b) {
        set(EXPERT_VISIBLE, b);
        return this;
    }

    @Override
    protected void attached() {
        bind();
    }

    @Override
    protected void resync() {
        push();
    }

    @Override
    protected void detached() {
        if (em != null) {
            em.removePropertyChangeListener(emListener);
            em = null;
        }
        listen(false);
    }

    private void bind() {
        if (em != null) {
            em.removePropertyChangeListener(emListener);
        }
        em = explicitManager != null ? explicitManager : ExplorerManager.find(this);
        if (em != null) {
            em.addPropertyChangeListener(emListener);
            if (!explicitNodes) {
                setNodesInternal(em.getSelectedNodes());
                return;
            }
        }
        listen(true);
        push();
    }

    private void listen(boolean on) {
        for (Node n : nodes) {
            if (on) {
                n.addNodeListener(nodeListener);
            } else {
                n.removeNodeListener(nodeListener);
            }
        }
    }

    private void setNodesInternal(Node[] ns) {
        listen(false);
        nodes = ns.clone();
        if (isAttached()) {
            listen(true);
            push();
        }
    }

    /** Sends the whole sheet. */
    private void push() {
        List<Object> sets = new ArrayList<Object>();
        if (nodes.length > 0) {
            Node first = nodes[0];
            for (Node.PropertySet set : first.getPropertySets()) {
                if (set.isExpert() && !Boolean.TRUE.equals(get(EXPERT_VISIBLE))) {
                    continue;
                }
                List<Object> props = new ArrayList<Object>();
                for (Node.Property<?> p : set.getProperties()) {
                    if (p.isHidden() || !commonToAll(p)) {
                        continue;
                    }
                    props.add(describe(p));
                }
                if (props.isEmpty()) {
                    continue;
                }
                Map<String, Object> s = new LinkedHashMap<String, Object>();
                s.put("name", set.getName());
                s.put("label", set.getDisplayName() != null ? set.getDisplayName() : set.getName());
                s.put("expert", set.isExpert());
                s.put("props", props);
                sets.add(s);
            }
        }
        Map<String, Object> sheet = new LinkedHashMap<String, Object>();
        sheet.put("count", nodes.length);
        sheet.put("title", nodes.length == 1 ? nodes[0].getDisplayName() : null);
        Resource icon = nodes.length == 1 ? nodes[0].getIcon() : null;
        if (icon != null) {
            sheet.put("icon", icon);
        }
        sheet.put("sets", sets);
        call("sheet", sheet);
    }

    private boolean commonToAll(Node.Property<?> p) {
        for (int i = 1; i < nodes.length; i++) {
            Node.Property<?> q = nodes[i].getProperty(p.getName());
            if (q == null || q.getValueType() != p.getValueType()) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> describe(Node.Property<?> p) {
        Map<String, Object> d = new LinkedHashMap<String, Object>();
        d.put("name", p.getName());
        d.put("label", p.getDisplayName() != null ? p.getDisplayName() : p.getName());
        if (p.getShortDescription() != null) {
            d.put("desc", p.getShortDescription());
        }
        String text = p.getValueAsText();
        boolean same = true;
        for (int i = 1; i < nodes.length && same; i++) {
            Node.Property<?> q = nodes[i].getProperty(p.getName());
            String t = q == null ? null : q.getValueAsText();
            same = text == null ? t == null : text.equals(t);
        }
        d.put("text", same ? (text == null ? "" : text) : "");
        d.put("mixed", !same);
        if (same) {
            org.webbeans.ui.table.Cell deco = p.getValueAsCell();
            if (deco != null) {
                d.put("cell", deco);
            }
        }
        boolean writable = p.canWrite();
        for (int i = 1; i < nodes.length && writable; i++) {
            Node.Property<?> q = nodes[i].getProperty(p.getName());
            writable = q != null && q.canWrite();
        }
        d.put("canWrite", writable);
        PropertyEditor<?> e = p.getPropertyEditor();
        d.put("kind", e == null ? "TEXT" : e.getKind().name());
        if (e != null && e.getChoices() != null) {
            List<Object> choices = new ArrayList<Object>();
            for (PropertyEditor.Choice<?> c : e.getChoices()) {
                Map<String, Object> ch = new LinkedHashMap<String, Object>();
                ch.put("value", textOf(e, c.getValue()));
                ch.put("label", c.getLabel());
                if (c.getIcon() != null) {
                    ch.put("icon", c.getIcon());
                }
                choices.add(ch);
            }
            d.put("choices", choices);
        }
        if (e != null && !e.getClientConfig().isEmpty()) {
            d.put("config", e.getClientConfig());
        }
        return d;
    }

    @SuppressWarnings("unchecked")
    private static <T> String textOf(PropertyEditor<T> e, Object v) {
        return e.getAsText((T) v);
    }

    private void edit(String propName, String text) {
        if (propName == null) {
            return;
        }
        for (Node n : nodes) {
            Node.Property<?> p = n.getProperty(propName);
            if (p == null || !p.canWrite()) {
                continue;
            }
            try {
                NodeView.setFromText(p, text);
            } catch (Exception ex) {
                Map<String, Object> err = new LinkedHashMap<String, Object>();
                err.put("prop", propName);
                err.put("message", ex.getMessage() == null ? ex.toString() : ex.getMessage());
                call("editError", err);
                return;
            }
        }
        push(); // the model may have normalised the value; nodes without change events get refreshed too
    }
}
