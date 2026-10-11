package org.webbeans.explorer;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.beans.VetoableChangeSupport;
import java.util.Arrays;

import org.webbeans.nodes.Node;
import org.webbeans.ui.Component;

/**
 * The selection model shared by the explorer views of one window: a root context, an explored context (the
 * folder a list view shows) and the selected nodes. Views find it through {@link Provider} on an enclosing
 * component (usually the {@code TopComponent}).
 */
public final class ExplorerManager {

    public static final String PROP_ROOT_CONTEXT = "rootContext";
    public static final String PROP_EXPLORED_CONTEXT = "exploredContext";
    public static final String PROP_SELECTED_NODES = "selectedNodes";

    /** Implemented by the component (window) that owns the manager. */
    public interface Provider {
        ExplorerManager getExplorerManager();
    }

    private static final Node[] NONE = new Node[0];

    private Node root;
    private Node explored;
    private Node[] selected = NONE;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final VetoableChangeSupport vcs = new VetoableChangeSupport(this);

    /** The manager of the nearest enclosing {@link Provider}, or null. */
    public static ExplorerManager find(Component c) {
        for (Component x = c; x != null; x = x.getParent()) {
            if (x instanceof Provider) {
                return ((Provider) x).getExplorerManager();
            }
        }
        return null;
    }

    public Node getRootContext() {
        return root;
    }

    public void setRootContext(Node n) {
        Node old = root;
        root = n;
        explored = n;
        setSelectedNodesInternal(NONE);
        pcs.firePropertyChange(PROP_ROOT_CONTEXT, old, n);
        pcs.firePropertyChange(PROP_EXPLORED_CONTEXT, old, n);
    }

    public Node getExploredContext() {
        return explored != null ? explored : root;
    }

    public void setExploredContext(Node n) {
        setExploredContextAndSelection(n, NONE);
    }

    public void setExploredContextAndSelection(Node n, Node[] selection) {
        Node old = explored;
        explored = n;
        setSelectedNodesInternal(selection == null ? NONE : selection);
        pcs.firePropertyChange(PROP_EXPLORED_CONTEXT, old, n);
    }

    public Node[] getSelectedNodes() {
        return selected.clone();
    }

    /** Changes the selection unless a vetoable listener objects. */
    public void setSelectedNodes(Node[] nodes) throws PropertyVetoException {
        Node[] n = nodes == null ? NONE : nodes.clone();
        if (Arrays.equals(n, selected)) {
            return;
        }
        vcs.fireVetoableChange(PROP_SELECTED_NODES, selected, n);
        setSelectedNodesInternal(n);
    }

    private void setSelectedNodesInternal(Node[] n) {
        Node[] old = selected;
        selected = n;
        pcs.firePropertyChange(PROP_SELECTED_NODES, old, n);
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    public void addVetoableChangeListener(VetoableChangeListener l) {
        vcs.addVetoableChangeListener(l);
    }

    public void removeVetoableChangeListener(VetoableChangeListener l) {
        vcs.removeVetoableChangeListener(l);
    }
}
