package org.webbeans.nodes;

import java.io.IOException;
import java.util.Map;

/**
 * Restores a node from a {@link Node.Handle}'s data; registered under {@code Nodes/Handles/<type>}:
 * {@code @Registration(path = "Nodes/Handles", name = "file", type = NodeHandleFactory.class)}.
 */
public interface NodeHandleFactory {

    /** The node, or null when it no longer exists. */
    Node restore(Map<String, Object> data) throws IOException;
}
