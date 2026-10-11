package org.webbeans.actions;

import java.util.prefs.Preferences;

import org.webbeans.core.registry.Entry;

/** A check item bound to a boolean preference; toggling writes the preference and runs the listener. */
final class CheckboxAction extends RegistryAction {

    private final java.util.function.Supplier<Preferences> prefs;
    private final String key;
    private final boolean defaultValue;
    private final ActionListener listener;

    CheckboxAction(java.util.function.Supplier<Preferences> prefs, String key, boolean defaultValue, ActionListener listener, Entry entry) {
        super(entry);
        this.prefs = prefs;
        this.key = key;
        this.defaultValue = defaultValue;
        this.listener = listener;
    }

    @Override
    public Object getValue(String k) {
        if (SELECTED.equals(k)) {
            Preferences p = prefs.get();
            return p == null ? defaultValue : p.getBoolean(key, defaultValue);
        }
        return super.getValue(k);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        boolean now = !Boolean.TRUE.equals(getValue(SELECTED));
        Preferences p = prefs.get();
        if (p != null) {
            p.putBoolean(key, now);
        }
        firePropertyChange(SELECTED, !now, now);
        if (listener != null) {
            listener.actionPerformed(e);
        }
    }
}
