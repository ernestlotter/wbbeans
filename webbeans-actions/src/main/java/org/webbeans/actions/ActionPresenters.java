package org.webbeans.actions;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Resource;

/**
 * Default presenters for actions and the binding that keeps a presenter in sync with its action: enabled,
 * selected, text and icon follow the action's properties (and are re-read whenever the context lookup
 * changes, for actions that compute their state), choosing the presenter invokes the action with the context.
 */
public final class ActionPresenters {

    private ActionPresenters() {
    }

    /** A menu item for the action over the context (a {@link Presenter.Menu} action supplies its own component). */
    public static Component menuItem(Action a, Lookup context) {
        if (a instanceof Presenter.Menu) {
            return ((Presenter.Menu) a).getMenuPresenter();
        }
        return new MenuItem().setAction(a, context, false);
    }

    /** A popup item for the action over the selection context. */
    public static Component popupItem(Action a, Lookup context) {
        if (a instanceof Presenter.Popup) {
            return ((Presenter.Popup) a).getPopupPresenter();
        }
        return new MenuItem().setAction(a, context, true);
    }

    /** A toolbar button for the action over the context. */
    public static Component toolbarButton(Action a, Lookup context) {
        if (a instanceof Presenter.Toolbar) {
            return ((Presenter.Toolbar) a).getToolbarPresenter();
        }
        return new ToolbarButton().setAction(a, context);
    }

    /** Bound actions that hold listeners on shared objects release them here. */
    public interface Disposable {
        void dispose();
    }

    /** Keeps one presenter and one action in sync while the presenter is attached. */
    static final class Binding implements PropertyChangeListener, LookupListener {
        private final Action template;
        private final Lookup context;
        private final Component presenter;
        private final boolean popup;
        private Action action;
        private Lookup.Result<Object> contextResult;
        private org.webbeans.ui.Registration eventRegistration;

        Binding(Action template, Lookup context, Component presenter, boolean popup) {
            this.template = template;
            this.context = context == null ? Lookup.EMPTY : context;
            this.presenter = presenter;
            this.popup = popup;
            this.action = template;
        }

        Action getAction() {
            return action;
        }

        void start() {
            if (eventRegistration != null) {
                return;
            }
            action = template instanceof ContextAwareAction ? ((ContextAwareAction) template).createContextAwareInstance(context) : template;
            action.addPropertyChangeListener(this);
            contextResult = context.lookupResult(Object.class);
            contextResult.addLookupListener(this);
            Listener<Event> l = new Listener<Event>() {
                @Override
                public void onEvent(Event e) {
                    Actions.invoke(action, presenter, context);
                }
            };
            eventRegistration = presenter instanceof MenuItem ? ((MenuItem) presenter).addActionListener(l)
                    : ((ToolbarButton) presenter).addActionListener(l);
            refresh();
        }

        void stop() {
            if (eventRegistration == null) {
                return;
            }
            eventRegistration.remove();
            eventRegistration = null;
            action.removePropertyChangeListener(this);
            contextResult.removeLookupListener(this);
            contextResult = null;
            if (action instanceof Disposable) {
                ((Disposable) action).dispose();
            }
            action = template;
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            refresh();
        }

        @Override
        public void resultChanged(LookupEvent ev) {
            refresh();
        }

        void refresh() {
            if (presenter.getUI() == null) {
                return;
            }
            if (presenter.getUI().getSession().isSessionThread()) {
                apply();
                return;
            }
            presenter.getUI().access(new Runnable() {
                @Override
                public void run() {
                    apply();
                }
            });
        }

        private void apply() {
            Object sel = action.getValue(Action.SELECTED);
            boolean enabled = action.isEnabled();
            if (presenter instanceof MenuItem) {
                MenuItem mi = (MenuItem) presenter;
                mi.setText(popup ? Actions.popupText(action) : Actions.menuText(action));
                boolean noIcon = Boolean.TRUE.equals(action.getValue(Action.NO_ICON_IN_MENU));
                mi.setIcon(noIcon ? null : action.getIcon());
                Object acc = action.getValue(Action.ACCELERATOR);
                mi.setAccelerator(acc == null ? null : Utilities.keyToDisplay(acc.toString()));
                mi.setSelected(sel instanceof Boolean ? (Boolean) sel : null);
                mi.setKind(sel instanceof Boolean ? MenuItem.Kind.CHECK : MenuItem.Kind.ITEM);
                mi.setEnabled(enabled);
            } else if (presenter instanceof ToolbarButton) {
                ToolbarButton b = (ToolbarButton) presenter;
                Resource icon = action.getIcon();
                b.setIcon(icon);
                b.setText(icon == null ? action.getDisplayName() : null);
                Object tip = action.getValue(Action.SHORT_DESCRIPTION);
                Object acc = action.getValue(Action.ACCELERATOR);
                String tooltip = tip != null ? tip.toString() : action.getDisplayName();
                if (acc != null) {
                    tooltip += " (" + Utilities.keyToDisplay(acc.toString()) + ")";
                }
                b.setTooltip(tooltip);
                b.setSelected(sel instanceof Boolean ? (Boolean) sel : null);
                b.setEnabled(enabled);
            }
        }
    }
}
