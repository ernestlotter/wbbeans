package org.webbeans.explorer;

import org.webbeans.core.registry.Renderer;
import org.webbeans.nodes.Node;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;

/**
 * The children of the explored context as a flat list; double-clicking a folder-like node makes it the explored
 * context (traversal), double-clicking a leaf runs its preferred action.
 */
@Renderer(id = "wb.ListView", resource = "org/webbeans/nodes/client/listview.js")
public class ListView extends NodeView {

    public static final Property<Boolean> SHOW_PARENT_NODE = Property.of("showParentNode", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> TRAVERSAL_ALLOWED = Property.of("traversalAllowed", Boolean.class, Boolean.TRUE);
    public static final Property<Integer> ICON_SIZE = Property.of("iconSize", Integer.class, 16);
    /** {n}: traverse into a node; {up: true}: go to the parent. */
    public static final EventType<Event> TRAVERSE = EventType.of("traverse", Event::new);

    public ListView() {
        super("wb.ListView");
        addListener(TRAVERSE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                ExplorerManager em = getExplorerManager();
                if (em == null || !Boolean.TRUE.equals(get(TRAVERSAL_ALLOWED))) {
                    return;
                }
                if (e.getBoolean("up")) {
                    Node explored = em.getExploredContext();
                    if (explored != null && explored.getParentNode() != null && explored != em.getRootContext()) {
                        em.setExploredContextAndSelection(explored.getParentNode(), new Node[] {explored});
                    }
                    return;
                }
                Node n = node(e.getInt("n", -1));
                if (n != null && !n.isLeaf()) {
                    em.setExploredContext(n);
                }
            }
        });
    }

    @Override
    protected Node rootNode() {
        return getExplorerManager() == null ? null : getExplorerManager().getExploredContext();
    }

    @Override
    protected void exploredContextChanged() {
        rebuild();
    }

    @Override
    protected void rebuild() {
        super.rebuild();
        ExplorerManager em = getExplorerManager();
        Node explored = rootNode();
        java.util.Map<String, Object> ctx = new java.util.LinkedHashMap<String, Object>();
        if (explored != null) {
            ctx.put("n", idOf(explored));
            ctx.put("dn", explored.getDisplayName());
            ctx.put("hasParent", explored.getParentNode() != null && em != null && explored != em.getRootContext());
        }
        call("context", ctx);
    }

    public ListView setShowParentNode(boolean b) {
        set(SHOW_PARENT_NODE, b);
        return this;
    }

    public ListView setTraversalAllowed(boolean b) {
        set(TRAVERSAL_ALLOWED, b);
        return this;
    }

    public ListView setIconSize(int px) {
        set(ICON_SIZE, px);
        return this;
    }
}
