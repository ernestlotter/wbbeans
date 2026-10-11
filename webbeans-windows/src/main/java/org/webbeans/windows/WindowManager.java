package org.webbeans.windows;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collection;
import java.util.Set;

import org.openide.util.Lookup;
import org.webbeans.ui.UI;

/**
 * The window system of one browser tab ({@link UI}): modes, open components, activation, layouts. Obtain it with
 * {@link #getDefault()} on the session executor, or from {@code ui.getLookup()}. Every method must be called on
 * the session executor.
 */
public abstract class WindowManager {

    /** Observable state, as NetBeans' {@code WindowManager.getRegistry()}. */
    public static final class Registry {
        public static final String PROP_OPENED = "opened";
        public static final String PROP_ACTIVATED = "activated";
        public static final String PROP_ACTIVATED_NODES = "activatedNodes";
        public static final String PROP_TC_OPENED = "tcOpened";
        public static final String PROP_TC_CLOSED = "tcClosed";

        private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
        private final WindowManager wm;

        Registry(WindowManager wm) {
            this.wm = wm;
        }

        public Set<TopComponent> getOpened() {
            return new java.util.LinkedHashSet<TopComponent>(wm.getOpened());
        }

        public TopComponent getActivated() {
            return wm.getActivated();
        }

        /** The objects of the activated component's lookup that the Nodes API (step 4) marks as nodes; empty for now. */
        public Collection<?> getActivatedNodes() {
            return wm.getActivatedNodes();
        }

        public void addPropertyChangeListener(PropertyChangeListener l) {
            pcs.addPropertyChangeListener(l);
        }

        public void removePropertyChangeListener(PropertyChangeListener l) {
            pcs.removePropertyChangeListener(l);
        }

        void fire(String prop, Object oldValue, Object newValue) {
            pcs.firePropertyChange(prop, oldValue, newValue);
        }
    }

    private final Registry registry = new Registry(this);

    /** The window manager of the current UI, or null when the current UI has none (no window system installed). */
    public static WindowManager getDefault() {
        UI ui = UI.current();
        return ui == null ? null : ui.getLookup().lookup(WindowManager.class);
    }

    public abstract UI getUI();

    public abstract Mode findMode(String name);

    public abstract Set<? extends Mode> getModes();

    public abstract Mode findMode(TopComponent tc);

    /**
     * The component with that id: an open or previously created instance, else a new one from the registry
     * ({@code Windows/Components/<id>}); null if unknown or not allowed for the user's roles.
     */
    public abstract TopComponent findTopComponent(String id);

    public abstract Collection<TopComponent> getOpened();

    /** The component's id in this UI (null before its first open), as NetBeans' {@code findTopComponentID}. */
    public String findTopComponentID(TopComponent tc) {
        return tc.getTopComponentId();
    }

    public abstract TopComponent getActivated();

    /** A lookup that always reflects the activated component's lookup (NetBeans' {@code actionsGlobalContext}). */
    public abstract Lookup getGlobalContext();

    public Registry getRegistry() {
        return registry;
    }

    public abstract TopComponentGroup findTopComponentGroup(String name);

    /** Switches to the layout of another role (saved per role); the current layout is saved first. */
    public abstract void setRole(String role);

    public abstract String getRole();

    /** Saves the current layout under a name ({@code layouts/<name>.json}). */
    public abstract void saveLayout(String name);

    /** Loads a named layout; unknown names are ignored. */
    public abstract boolean loadLayout(String name);

    /** Rebuilds the layout from the registry defaults (closing everything not opened at startup). */
    public abstract void resetLayout();

    /** Runs after the initial layout has been restored (immediately if it already has). */
    public abstract void invokeWhenUIReady(Runnable r);

    public abstract void setMaximized(TopComponent tc, boolean maximized);

    public abstract boolean isMaximized(TopComponent tc);

    public abstract void setFloating(TopComponent tc, boolean floating);

    /** Minimises the component's mode into the side bar; restore with {@code false}. */
    public abstract void setMinimized(TopComponent tc, boolean minimized);

    /** Closes every open component ({@code canClose} honoured unless forced). Returns false if one vetoed. */
    public abstract boolean closeAll(boolean force);

    // ---- driven by TopComponent ---------------------------------------------------------------------------------

    abstract void open(TopComponent tc, Mode mode);

    abstract boolean close(TopComponent tc, boolean force);

    abstract void requestActive(TopComponent tc);

    abstract void requestVisible(TopComponent tc);

    abstract Collection<?> getActivatedNodes();
}
