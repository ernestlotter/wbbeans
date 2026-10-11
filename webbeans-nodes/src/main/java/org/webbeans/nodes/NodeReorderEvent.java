package org.webbeans.nodes;

/** The children of a node changed order; {@link #getPermutation} maps new index → old index is not needed by views, which re-read the order. */
public final class NodeReorderEvent extends NodeEvent {

    private static final long serialVersionUID = 1L;
    private final transient int[] permutation;

    NodeReorderEvent(Node source, int[] permutation) {
        super(source);
        this.permutation = permutation;
    }

    public int[] getPermutation() {
        return permutation.clone();
    }
}
