package org.webbeans.nodes;

import java.beans.PropertyChangeEvent;

/** Empty {@link NodeListener}. */
public class NodeAdapter implements NodeListener {

    @Override
    public void childrenAdded(NodeMemberEvent ev) {
    }

    @Override
    public void childrenRemoved(NodeMemberEvent ev) {
    }

    @Override
    public void childrenReordered(NodeReorderEvent ev) {
    }

    @Override
    public void nodeDestroyed(NodeEvent ev) {
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
    }
}
