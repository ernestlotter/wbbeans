package org.webbeans.actions;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.webbeans.core.registry.Entry;

/**
 * A global action that delegates to the {@link ActionMap} entry of its key found in the context (the activated
 * window's map): Copy, Paste, Delete, Find... Enabled iff the map has an enabled action for the key, or the
 * fallback is enabled when the map has none.
 */
final class CallbackAction extends RegistryAction implements ContextAwareAction {

    private final String key;
    private final Action fallback;
    private final boolean surviveFocusChange;

    CallbackAction(String key, Action fallback, boolean surviveFocusChange, Entry entry) {
        super(entry);
        this.key = key;
        this.fallback = fallback;
        this.surviveFocusChange = surviveFocusChange;
    }

    String getKey() {
        return key;
    }

    Action delegate(Lookup context) {
        ActionMap map = context.lookup(ActionMap.class);
        Action a = map == null ? null : map.get(key);
        return a != null ? a : fallback;
    }

    @Override
    public boolean isEnabled() {
        Action d = delegate(Utilities.actionsGlobalContext());
        return super.isEnabled() && d != null && d.isEnabled();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Action d = delegate(e.getLookup());
        if (d != null && d.isEnabled()) {
            d.actionPerformed(e);
        }
    }

    @Override
    public Action createContextAwareInstance(Lookup context) {
        return new Bound(context);
    }

    private final class Bound extends AbstractAction implements ContextAwareAction, LookupListener, PropertyChangeListener, ActionPresenters.Disposable {
        private final Lookup context;
        private final Lookup.Result<ActionMap> result;
        private Action current;
        private ActionMap lastMap;
        private boolean enabled;

        Bound(Lookup context) {
            this.context = context;
            this.result = context.lookupResult(ActionMap.class);
            this.result.addLookupListener(this);
            CallbackAction.this.addPropertyChangeListener(this);
            retarget();
        }

        private void retarget() {
            ActionMap map = null;
            for (ActionMap m : result.allInstances()) {
                map = m;
                break;
            }
            if (map == null && surviveFocusChange) {
                map = lastMap;
            }
            lastMap = map;
            Action a = map == null ? null : map.get(key);
            if (a == null) {
                a = fallback;
            }
            if (a != current) {
                if (current != null) {
                    current.removePropertyChangeListener(this);
                }
                current = a;
                if (current != null) {
                    current.addPropertyChangeListener(this);
                }
            }
            recompute();
        }

        private void recompute() {
            // the map may have been replaced in place (same lookup, new entries): re-read the key each time
            Action a = lastMap == null ? null : lastMap.get(key);
            if (a == null) {
                a = fallback;
            }
            if (a != current) {
                retarget();
                return;
            }
            boolean now = CallbackAction.super.isEnabled() && current != null && current.isEnabled();
            if (now != enabled) {
                enabled = now;
                firePropertyChange(ENABLED, !now, now);
            }
        }

        @Override
        public void resultChanged(LookupEvent ev) {
            retarget();
        }

        @Override
        public void dispose() {
            result.removeLookupListener(this);
            CallbackAction.this.removePropertyChangeListener(this);
            if (current != null) {
                current.removePropertyChangeListener(this);
            }
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            recompute();
        }

        /** Re-evaluates (the delegate may compute its enabled state without firing changes). */
        void refresh() {
            recompute();
        }

        @Override
        public boolean isEnabled() {
            recompute();
            return enabled;
        }

        @Override
        public void setEnabled(boolean b) {
            CallbackAction.this.setEnabled(b);
        }

        @Override
        public Object getValue(String key) {
            Object v = super.getValue(key);
            return v != null ? v : CallbackAction.this.getValue(key);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            recompute();
            if (enabled && current != null) {
                current.actionPerformed(e.getLookup() == context ? e : new ActionEvent(e.getSource(), context, e.getActionCommand()));
            }
        }

        @Override
        public Action createContextAwareInstance(Lookup other) {
            return CallbackAction.this.createContextAwareInstance(other);
        }
    }
}
