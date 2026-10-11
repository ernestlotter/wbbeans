package org.webbeans.ui.table;

import java.util.List;

/**
 * The hierarchy behind a {@link TreeTable}: roots, children per node and the key that identifies a node
 * (default {@code toString}). Children are asked for when a node is expanded — the model can load them lazily;
 * the table flattens the expanded part into the rows it shows.
 */
public interface TreeTableModel<T> {

    List<? extends T> getRoots();

    List<? extends T> getChildren(T parent);

    boolean isLeaf(T node);

    default String getKey(T node) {
        return String.valueOf(node);
    }
}
