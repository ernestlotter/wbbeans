package org.webbeans.actions;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.webbeans.core.registry.Entry;

/**
 * A selection-sensitive action over objects of type {@code T}: the template computes its state from the current
 * UI's global context on demand; {@link #createContextAwareInstance} returns an instance bound to one lookup
 * that listens to it and fires {@link #ENABLED} changes.
 */
final class ContextAction<T> extends RegistryAction implements ContextAwareAction {

    /** What a context action does, given the objects of its type found in the context. */
    interface Performer<T> {
        void perform(List<? extends T> context, ActionEvent e);

        /** Additional enablement check over the matched objects (default: enabled when the count is right). */
        boolean enabled(List<? extends T> context);
    }

    private final Class<T> type;
    private final boolean single;
    private final boolean surviveFocusChange;
    private final Performer<T> performer;

    ContextAction(Class<T> type, boolean single, boolean surviveFocusChange, Performer<T> performer, Entry entry) {
        super(entry);
        this.type = type;
        this.single = single;
        this.surviveFocusChange = surviveFocusChange;
        this.performer = performer;
    }

    Class<T> getType() {
        return type;
    }

    List<T> collect(Lookup context) {
        return new ArrayList<T>(context.lookupAll(type));
    }

    boolean enabledFor(List<T> data) {
        if (data.isEmpty() || (single && data.size() != 1)) {
            return false;
        }
        return performer.enabled(data);
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled() && enabledFor(collect(Utilities.actionsGlobalContext()));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        perform(collect(e.getLookup()), e);
    }

    private void perform(final List<T> data, final ActionEvent e) {
        if (!enabledFor(data)) {
            return;
        }
        run(new Runnable() {
            @Override
            public void run() {
                performer.perform(data, e);
            }
        }, e);
    }

    @Override
    public Action createContextAwareInstance(Lookup context) {
        return new Bound(context);
    }

    /** The instance bound to one lookup; its enabled state follows the lookup's content. */
    private final class Bound extends AbstractAction implements ContextAwareAction, LookupListener, PropertyChangeListener, ActionPresenters.Disposable {
        private final Lookup context;
        private final Lookup.Result<T> result;
        private List<T> last = Collections.emptyList();
        private boolean enabled;

        Bound(Lookup context) {
            this.context = context;
            this.result = context.lookupResult(type);
            this.result.addLookupListener(this);
            ContextAction.this.addPropertyChangeListener(this);
            recompute();
        }

        private List<T> current() {
            List<T> data = new ArrayList<T>(result.allInstances());
            if (data.isEmpty() && surviveFocusChange && !last.isEmpty()) {
                return last;
            }
            last = data;
            return data;
        }

        private void recompute() {
            boolean now = ContextAction.super.isEnabled() && enabledFor(current());
            if (now != enabled) {
                enabled = now;
                firePropertyChange(ENABLED, !now, now);
            }
        }

        @Override
        public void resultChanged(LookupEvent ev) {
            recompute();
        }

        @Override
        public void dispose() {
            result.removeLookupListener(this);
            ContextAction.this.removePropertyChangeListener(this);
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (ENABLED.equals(evt.getPropertyName())) {
                recompute();
            } else {
                firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
            }
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void setEnabled(boolean b) {
            ContextAction.this.setEnabled(b);
        }

        @Override
        public Object getValue(String key) {
            Object v = super.getValue(key);
            return v != null ? v : ContextAction.this.getValue(key);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            List<T> data = current();
            ActionEvent withContext = e.getLookup() == context ? e : new ActionEvent(e.getSource(), context, e.getActionCommand());
            perform(data, withContext);
        }

        @Override
        public Action createContextAwareInstance(Lookup other) {
            return ContextAction.this.createContextAwareInstance(other);
        }

        @Override
        public String toString() {
            return "Bound" + ContextAction.this;
        }
    }
}
