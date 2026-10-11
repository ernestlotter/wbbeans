package org.webbeans.nodes;

import java.beans.FeatureDescriptor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.openide.util.Lookup;
import org.webbeans.actions.Action;
import org.webbeans.ui.Resource;
import org.webbeans.ui.UISession;

/**
 * A node of the explorer, as in NetBeans' {@code org.openide.nodes.Node}: a display name, icon, children,
 * properties, actions and a lookup with the represented object and its capabilities ({@link Openable},
 * {@link Savable}...). Nodes are UI-agnostic: many views may show one node, each keeping its own expansion and
 * selection state. All mutations happen on the session executor so views can react synchronously.
 */
public abstract class Node implements Lookup.Provider {

    public static final String PROP_NAME = "name";
    public static final String PROP_DISPLAY_NAME = "displayName";
    public static final String PROP_SHORT_DESCRIPTION = "shortDescription";
    public static final String PROP_ICON = "icon";
    public static final String PROP_OPENED_ICON = "openedIcon";
    public static final String PROP_LEAF = "leaf";
    public static final String PROP_PROPERTY_SETS = "propertySets";
    public static final String PROP_PARENT_NODE = "parentNode";

    /** A property of a node (one row of the property sheet, one column of an outline). */
    public abstract static class Property<T> extends FeatureDescriptor {
        private final Class<T> valueType;
        private PropertyEditor<T> editor;
        private java.util.function.Function<T, org.webbeans.ui.table.Cell> renderer;

        protected Property(Class<T> valueType) {
            this.valueType = valueType;
        }

        public Class<T> getValueType() {
            return valueType;
        }

        public abstract boolean canRead();

        public abstract boolean canWrite();

        public abstract T getValue() throws IllegalAccessException, InvocationTargetException;

        public abstract void setValue(T value) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException;

        /** The editor for this property; by default the registered one for the value type. */
        public PropertyEditor<T> getPropertyEditor() {
            return editor != null ? editor : PropertyEditors.forType(valueType);
        }

        public void setPropertyEditor(PropertyEditor<T> e) {
            this.editor = e;
        }

        /**
         * Custom display of the value in cells and sheets (icon, style, HTML) — takes precedence over the editor's
         * {@link PropertyEditor#getAsCell}; return null from the function to fall back to text.
         */
        public void setRenderer(java.util.function.Function<T, org.webbeans.ui.table.Cell> renderer) {
            this.renderer = renderer;
        }

        /** The decorated display of the current value, or null when it is plain text (see {@link #getValueAsText}). */
        public org.webbeans.ui.table.Cell getValueAsCell() {
            if (!canRead()) {
                return null;
            }
            try {
                T v = getValue();
                if (renderer != null) {
                    org.webbeans.ui.table.Cell c = renderer.apply(v);
                    if (c != null) {
                        return c;
                    }
                }
                PropertyEditor<T> e = getPropertyEditor();
                return e != null ? e.getAsCell(v) : null;
            } catch (Exception e) {
                return null;
            }
        }

        public boolean supportsDefaultValue() {
            return false;
        }

        public void restoreDefaultValue() throws IllegalAccessException, InvocationTargetException {
        }

        public String getHtmlDisplayName() {
            return null;
        }

        /** Convenience: the value as displayed, or {@code null} when unreadable. */
        public String getValueAsText() {
            if (!canRead()) {
                return null;
            }
            try {
                T v = getValue();
                PropertyEditor<T> e = getPropertyEditor();
                return e != null ? e.getAsText(v) : (v == null ? "" : String.valueOf(v));
            } catch (Exception e) {
                return "<" + e.getClass().getSimpleName() + ">";
            }
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Property && getName() != null && getName().equals(((Property<?>) o).getName())
                    && valueType == ((Property<?>) o).valueType;
        }

        @Override
        public int hashCode() {
            return getName() == null ? 0 : getName().hashCode();
        }
    }

    /** A named group of properties (a collapsible section of the sheet). */
    public abstract static class PropertySet extends FeatureDescriptor {
        public abstract Property<?>[] getProperties();
    }

    /** A persistable pointer to a node, restored by the {@link NodeHandleFactory} registered under {@code Nodes/Handles/<type>}. */
    public interface Handle {
        /** The factory type id. */
        String getType();

        /** Plain data (strings, numbers, lists, maps) that identify the node. */
        Map<String, Object> toMap();
    }

    private Children children;
    private Node parent;
    private String name;
    private String displayName;
    private String shortDescription;
    private Resource icon;
    private Resource openedIcon;
    private final Lookup lookup;
    private final List<NodeListener> listeners = new ArrayList<NodeListener>();
    private final List<PropertyChangeListener> propertyListeners = new ArrayList<PropertyChangeListener>();

    protected Node(Children children) {
        this(children, null);
    }

    protected Node(Children children, Lookup lookup) {
        this.lookup = lookup;
        setChildren(children == null ? Children.LEAF : children);
    }

    // ---- identity & display ---------------------------------------------------------------------------------------

    public String getName() {
        return name;
    }

    public void setName(String n) {
        String old = name;
        name = n;
        if (old == null ? n != null : !old.equals(n)) {
            fireNameChange(old, n);
        }
    }

    public String getDisplayName() {
        return displayName != null ? displayName : name;
    }

    public void setDisplayName(String n) {
        String old = getDisplayName();
        displayName = n;
        fireDisplayNameChange(old, getDisplayName());
    }

    /** A restricted HTML label ({@code <b>}, {@code <i>}, {@code <font color>}...), or null for the plain name. */
    public String getHtmlDisplayName() {
        return null;
    }

    /** Tooltip. */
    public String getShortDescription() {
        return shortDescription != null ? shortDescription : getDisplayName();
    }

    public void setShortDescription(String s) {
        String old = shortDescription;
        shortDescription = s;
        firePropertyChange(PROP_SHORT_DESCRIPTION, old, s);
    }

    public Resource getIcon() {
        return icon;
    }

    public void setIcon(Resource r) {
        Resource old = icon;
        icon = r;
        if (old == null ? r != null : !old.equals(r)) {
            fireIconChange();
        }
    }

    /** Icon of an expanded folder-like node; defaults to {@link #getIcon()}. */
    public Resource getOpenedIcon() {
        return openedIcon != null ? openedIcon : getIcon();
    }

    public void setOpenedIcon(Resource r) {
        openedIcon = r;
        fireOpenedIconChange();
    }

    // ---- structure --------------------------------------------------------------------------------------------------

    public final Children getChildren() {
        return children;
    }

    protected final void setChildren(Children c) {
        if (children != null) {
            children.detach();
        }
        children = c;
        c.attach(this);
        firePropertyChange(PROP_LEAF, null, Boolean.valueOf(isLeaf()));
    }

    public final Node getParentNode() {
        return parent;
    }

    final void setParentNode(Node p) {
        Node old = parent;
        parent = p;
        if (old != p) {
            firePropertyChange(PROP_PARENT_NODE, old, p);
        }
    }

    public final boolean isLeaf() {
        return children == Children.LEAF;
    }

    // ---- behaviour ----------------------------------------------------------------------------------------------------

    /** The represented object and capabilities; {@code Lookup.EMPTY} when none was given. */
    @Override
    public Lookup getLookup() {
        return lookup != null ? lookup : Lookup.EMPTY;
    }

    /**
     * Actions of the popup menu ({@code context = true}) or of the node in general; {@code null} entries are
     * separators. Default: none.
     */
    public Action[] getActions(boolean context) {
        return new Action[0];
    }

    /** Double-click / Enter: the first {@link Openable} in the lookup, else the first enabled action, else null. */
    public Action getPreferredAction() {
        final Openable o = getLookup().lookup(Openable.class);
        if (o != null) {
            return new org.webbeans.actions.AbstractAction("open") {
                @Override
                public void actionPerformed(org.webbeans.actions.ActionEvent e) {
                    o.open();
                }
            };
        }
        for (Action a : getActions(false)) {
            if (a != null) {
                return a;
            }
        }
        return null;
    }

    public boolean canRename() {
        return false;
    }

    public boolean canDestroy() {
        return false;
    }

    public boolean canCut() {
        return false;
    }

    public boolean canCopy() {
        return true;
    }

    /** Removes the node from its parent and fires {@code nodeDestroyed}. Subclasses delete the represented object first. */
    public void destroy() throws IOException {
        if (parent != null && parent.getChildren() instanceof Children.Array) {
            ((Children.Array) parent.getChildren()).remove(this);
        }
        fireNodeDestroyed();
    }

    public Transferable clipboardCopy() throws IOException {
        return Transferables.nodes(this);
    }

    public Transferable clipboardCut() throws IOException {
        return Transferables.nodes(this);
    }

    public Transferable drag() throws IOException {
        return clipboardCopy();
    }

    /** How a transferable could be pasted here; empty when it cannot. */
    public PasteType[] getPasteTypes(Transferable t) {
        return new PasteType[0];
    }

    /** How a drop at the given index could be accepted; null when it cannot. Default: the first paste type. */
    public PasteType getDropType(Transferable t, DropAction action, int index) {
        PasteType[] types = getPasteTypes(t);
        return types.length == 0 ? null : types[0];
    }

    public enum DropAction { COPY, MOVE, LINK }

    // ---- properties ---------------------------------------------------------------------------------------------------

    public PropertySet[] getPropertySets() {
        return new PropertySet[0];
    }

    /** All properties of all sets, in order. */
    public final List<Property<?>> getProperties() {
        List<Property<?>> out = new ArrayList<Property<?>>();
        for (PropertySet set : getPropertySets()) {
            out.addAll(Arrays.asList(set.getProperties()));
        }
        return out;
    }

    /** The property with that name in any set, or null. */
    public final Property<?> getProperty(String propertyName) {
        for (Property<?> p : getProperties()) {
            if (propertyName.equals(p.getName())) {
                return p;
            }
        }
        return null;
    }

    /** A persistable pointer to this node, or null when it cannot be restored later. */
    public Handle getHandle() {
        return null;
    }

    // ---- events --------------------------------------------------------------------------------------------------------

    public void addNodeListener(NodeListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    public void removeNodeListener(NodeListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        synchronized (propertyListeners) {
            propertyListeners.add(l);
        }
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        synchronized (propertyListeners) {
            propertyListeners.remove(l);
        }
    }

    private List<NodeListener> nodeListeners() {
        synchronized (listeners) {
            return new ArrayList<NodeListener>(listeners);
        }
    }

    protected final void fireNameChange(String old, String n) {
        firePropertyChange(PROP_NAME, old, n);
        if (displayName == null) {
            firePropertyChange(PROP_DISPLAY_NAME, old, n);
        }
    }

    protected final void fireDisplayNameChange(String old, String n) {
        firePropertyChange(PROP_DISPLAY_NAME, old, n);
    }

    protected final void fireIconChange() {
        firePropertyChange(PROP_ICON, null, null);
    }

    protected final void fireOpenedIconChange() {
        firePropertyChange(PROP_OPENED_ICON, null, null);
    }

    protected final void firePropertySetsChange(PropertySet[] old, PropertySet[] n) {
        firePropertyChange(PROP_PROPERTY_SETS, old, n);
    }

    /** Fires a change of a node attribute or a {@link Property} value (by property name). */
    protected final void firePropertyChange(String propertyName, Object old, Object n) {
        checkThread();
        PropertyChangeEvent ev = new PropertyChangeEvent(this, propertyName, old, n);
        for (NodeListener l : nodeListeners()) {
            l.propertyChange(ev);
        }
        List<PropertyChangeListener> pls;
        synchronized (propertyListeners) {
            pls = new ArrayList<PropertyChangeListener>(propertyListeners);
        }
        for (PropertyChangeListener l : pls) {
            l.propertyChange(ev);
        }
    }

    protected final void fireNodeDestroyed() {
        NodeEvent ev = new NodeEvent(this);
        for (NodeListener l : nodeListeners()) {
            l.nodeDestroyed(ev);
        }
    }

    final void fireChildrenAdded(List<Node> added, int[] indices) {
        NodeMemberEvent ev = new NodeMemberEvent(this, true, added, indices);
        for (NodeListener l : nodeListeners()) {
            l.childrenAdded(ev);
        }
    }

    final void fireChildrenRemoved(List<Node> removed, int[] indices) {
        NodeMemberEvent ev = new NodeMemberEvent(this, false, removed, indices);
        for (NodeListener l : nodeListeners()) {
            l.childrenRemoved(ev);
        }
    }

    final void fireChildrenReordered(int[] permutation) {
        NodeReorderEvent ev = new NodeReorderEvent(this, permutation);
        for (NodeListener l : nodeListeners()) {
            l.childrenReordered(ev);
        }
    }

    static void checkThread() {
        if (UISession.isStrictThreading()) {
            UISession s = UISession.current();
            if (s != null && !s.isSessionThread()) {
                throw new IllegalStateException("Nodes must be modified on the session executor (use ui.access / session.access)");
            }
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + getDisplayName() + "]";
    }
}
