package org.webbeans.nodes;

import java.util.List;

import org.webbeans.core.i18n.Bundles;

/**
 * Computes the keys of a node's children and creates nodes for them, as NetBeans' {@code ChildFactory}. Use with
 * {@link Children#create(ChildFactory, boolean)}; an asynchronous factory computes keys on a background thread
 * while a "Please wait..." node is shown.
 */
public abstract class ChildFactory<T> {

    private FactoryChildren<T> children;

    /**
     * Adds the keys to the list; return {@code true} when done, {@code false} to be called again (batching long
     * lists). For asynchronous factories this runs off the session thread and must not touch components or nodes.
     */
    protected abstract boolean createKeys(List<T> toPopulate);

    /** One node for a key; return null to show nothing for it. Override {@link #createNodesForKey} for several. */
    protected Node createNodeForKey(T key) {
        return null;
    }

    protected Node[] createNodesForKey(T key) {
        Node n = createNodeForKey(key);
        return n == null ? new Node[0] : new Node[] {n};
    }

    /** The placeholder shown while an asynchronous factory computes its first batch. */
    protected Node createWaitNode() {
        AbstractNode n = new AbstractNode(Children.LEAF);
        n.setName("wait");
        n.setDisplayName(Bundles.getMessage(ChildFactory.class, "LBL_PleaseWait"));
        n.setIconBase("org/webbeans/nodes/client/wait.svg");
        return n;
    }

    /** Recomputes the keys; {@code immediate} runs synchronously even for asynchronous factories. */
    protected final void refresh(boolean immediate) {
        if (children != null) {
            children.refresh(immediate);
        }
    }

    void bind(FactoryChildren<T> c) {
        this.children = c;
    }
}
