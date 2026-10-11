package org.webbeans.explorer;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Property;

/** NetBeans' {@code BeanTreeView}: the root context as a tree; children are fetched on expansion. */
@Renderer(id = "wb.TreeView", resource = "org/webbeans/nodes/client/treeview.js", css = "org/webbeans/nodes/client/nodes.css")
public class TreeView extends NodeView {

    public static final Property<Boolean> ROOT_VISIBLE = Property.of("rootVisible", Boolean.class, Boolean.TRUE);

    public TreeView() {
        super("wb.TreeView");
    }

    protected TreeView(String rendererId) {
        super(rendererId);
    }

    public TreeView setRootVisible(boolean b) {
        set(ROOT_VISIBLE, b);
        return this;
    }

    public boolean isRootVisible() {
        return Boolean.TRUE.equals(get(ROOT_VISIBLE));
    }

    /**
     * Expands a node (sends its children); the client expands the row. Remembered: the node stays expanded
     * across reconnects and root rebuilds, and a node whose parent is not expanded yet expands once it is sent.
     */
    public void expandNode(org.webbeans.nodes.Node n) {
        expand(n);
    }

    public void collapseNode(org.webbeans.nodes.Node n) {
        collapse(n);
    }
}
