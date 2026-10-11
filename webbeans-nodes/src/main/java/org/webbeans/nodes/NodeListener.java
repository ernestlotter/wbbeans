package org.webbeans.nodes;

import java.beans.PropertyChangeListener;

/** Structural and property changes of a node. Use {@link NodeAdapter} for a partial implementation. */
public interface NodeListener extends PropertyChangeListener {

    void childrenAdded(NodeMemberEvent ev);

    void childrenRemoved(NodeMemberEvent ev);

    void childrenReordered(NodeReorderEvent ev);

    void nodeDestroyed(NodeEvent ev);
}
