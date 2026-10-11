package org.webbeans.nodes;

import java.util.Collections;
import java.util.List;

/** Children were added to or removed from a node; the delta and the indices they had (removed) or have (added). */
public final class NodeMemberEvent extends NodeEvent {

    private static final long serialVersionUID = 1L;
    private final boolean add;
    private final transient List<Node> delta;
    private final transient int[] indices;

    NodeMemberEvent(Node source, boolean add, List<Node> delta, int[] indices) {
        super(source);
        this.add = add;
        this.delta = Collections.unmodifiableList(delta);
        this.indices = indices;
    }

    public boolean isAddEvent() {
        return add;
    }

    public List<Node> getDelta() {
        return delta;
    }

    public int[] getDeltaIndices() {
        return indices.clone();
    }
}
