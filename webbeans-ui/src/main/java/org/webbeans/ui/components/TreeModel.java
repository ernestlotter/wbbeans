package org.webbeans.ui.components;

import java.util.List;

import org.webbeans.ui.Resource;

/**
 * The data behind a {@link Tree}: how to get a node's children, label, icon and key. Children are asked for
 * lazily, when a node is first expanded (and again on {@link Tree#refresh}). Keys must be unique in the whole
 * tree; the default is {@code toString()}.
 */
public interface TreeModel<T> {

    /** The children of {@code parent}; {@code null} asks for the roots. Called on the session executor. */
    List<T> getChildren(T parent);

    String getLabel(T node);

    default Resource getIcon(T node) {
        return null;
    }

    /** Leaves get no expander. The default looks at the children, so override it when that is expensive. */
    default boolean isLeaf(T node) {
        return getChildren(node).isEmpty();
    }

    default String getKey(T node) {
        return String.valueOf(node);
    }
}
