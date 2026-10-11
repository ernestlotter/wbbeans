package org.webbeans.nodes;

import java.util.EventObject;

public class NodeEvent extends EventObject {

    private static final long serialVersionUID = 1L;

    public NodeEvent(Node source) {
        super(source);
    }

    public Node getNode() {
        return (Node) getSource();
    }
}
