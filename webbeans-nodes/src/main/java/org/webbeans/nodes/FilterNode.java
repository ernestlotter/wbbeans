package org.webbeans.nodes;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.openide.util.Lookup;
import org.webbeans.actions.Action;
import org.webbeans.ui.Resource;

/**
 * Presents another node, possibly differently: every method delegates to the original unless overridden; the
 * children are filter nodes over the original's children (override {@link Children#createNodes} to filter or
 * wrap differently). Changes of the original are mirrored.
 */
public class FilterNode extends Node {

    private final Node original;
    private final NodeListener mirror = new NodeAdapter() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
        }

        @Override
        public void nodeDestroyed(NodeEvent ev) {
            fireNodeDestroyed();
        }
    };

    public FilterNode(Node original) {
        this(original, new Children(original), original.getLookup());
    }

    public FilterNode(Node original, org.webbeans.nodes.Children children) {
        this(original, children, original.getLookup());
    }

    public FilterNode(Node original, org.webbeans.nodes.Children children, Lookup lookup) {
        super(children, lookup);
        this.original = original;
        original.addNodeListener(mirror);
    }

    public final Node getOriginal() {
        return original;
    }

    @Override
    public String getName() {
        return original.getName();
    }

    @Override
    public void setName(String n) {
        original.setName(n);
    }

    @Override
    public String getDisplayName() {
        return original.getDisplayName();
    }

    @Override
    public String getHtmlDisplayName() {
        return original.getHtmlDisplayName();
    }

    @Override
    public String getShortDescription() {
        return original.getShortDescription();
    }

    @Override
    public Resource getIcon() {
        return original.getIcon();
    }

    @Override
    public Resource getOpenedIcon() {
        return original.getOpenedIcon();
    }

    @Override
    public Action[] getActions(boolean context) {
        return original.getActions(context);
    }

    @Override
    public Action getPreferredAction() {
        return original.getPreferredAction();
    }

    @Override
    public boolean canRename() {
        return original.canRename();
    }

    @Override
    public boolean canDestroy() {
        return original.canDestroy();
    }

    @Override
    public boolean canCut() {
        return original.canCut();
    }

    @Override
    public boolean canCopy() {
        return original.canCopy();
    }

    @Override
    public void destroy() throws IOException {
        original.destroy();
    }

    @Override
    public PasteType[] getPasteTypes(Transferable t) {
        return original.getPasteTypes(t);
    }

    @Override
    public PasteType getDropType(Transferable t, DropAction action, int index) {
        return original.getDropType(t, action, index);
    }

    @Override
    public PropertySet[] getPropertySets() {
        return original.getPropertySets();
    }

    @Override
    public Handle getHandle() {
        return original.getHandle();
    }

    /** Children that wrap the original's children in {@link FilterNode}s, following its changes. */
    public static class Children extends org.webbeans.nodes.Children.Keys<Node> {
        private final Node original;
        private final NodeListener listener = new NodeAdapter() {
            @Override
            public void childrenAdded(NodeMemberEvent ev) {
                sync();
            }

            @Override
            public void childrenRemoved(NodeMemberEvent ev) {
                sync();
            }

            @Override
            public void childrenReordered(NodeReorderEvent ev) {
                sync();
            }
        };

        public Children(Node original) {
            this.original = original;
        }

        @Override
        protected void addNotify() {
            original.addNodeListener(listener);
            sync();
        }

        @Override
        protected void removeNotify() {
            original.removeNodeListener(listener);
            super.removeNotify();
        }

        private void sync() {
            setKeys(new ArrayList<Node>(Arrays.asList(original.getChildren().getNodes())));
        }

        /** Wraps one original child; return an empty array to hide it. */
        @Override
        protected Node[] createNodes(Node key) {
            return new Node[] {new FilterNode(key)};
        }

        /** The original children, for subclasses that filter. */
        protected final List<Node> originalNodes() {
            return Arrays.asList(original.getChildren().getNodes());
        }
    }
}
