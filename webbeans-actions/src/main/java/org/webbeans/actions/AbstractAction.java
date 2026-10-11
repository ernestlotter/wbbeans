package org.webbeans.actions;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.LinkedHashMap;
import java.util.Map;

import org.webbeans.ui.Resource;

/** Base class: a value map, property change support and an enabled flag. */
public abstract class AbstractAction implements Action {

    private final Map<String, Object> values = new LinkedHashMap<String, Object>();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private boolean enabled = true;

    protected AbstractAction() {
    }

    protected AbstractAction(String displayName) {
        this(displayName, null);
    }

    protected AbstractAction(String displayName, Resource icon) {
        values.put(NAME, displayName);
        if (icon != null) {
            values.put(ICON, icon);
        }
    }

    @Override
    public String getDisplayName() {
        Object v = getValue(NAME);
        return v == null ? null : v.toString();
    }

    public void setDisplayName(String name) {
        putValue(NAME, name);
    }

    @Override
    public Resource getIcon() {
        Object v = getValue(ICON);
        return v instanceof Resource ? (Resource) v : null;
    }

    public void setIcon(Resource icon) {
        putValue(ICON, icon);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean b) {
        if (enabled != b) {
            enabled = b;
            pcs.firePropertyChange(ENABLED, !b, b);
        }
    }

    /** Checked state for checkbox items; null for plain items. */
    public Boolean getSelected() {
        Object v = getValue(SELECTED);
        return v instanceof Boolean ? (Boolean) v : null;
    }

    public void setSelected(boolean b) {
        putValue(SELECTED, b);
    }

    @Override
    public Object getValue(String key) {
        return values.get(key);
    }

    @Override
    public void putValue(String key, Object value) {
        Object old = values.put(key, value);
        if (old != value && (old == null || !old.equals(value))) {
            pcs.firePropertyChange(key, old, value);
        }
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    protected final void firePropertyChange(String name, Object oldValue, Object newValue) {
        pcs.firePropertyChange(name, oldValue, newValue);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + getDisplayName() + "]";
    }
}
