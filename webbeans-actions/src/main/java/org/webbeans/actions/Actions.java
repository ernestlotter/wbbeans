package org.webbeans.actions;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.openide.util.Lookup;
import org.webbeans.core.registry.Entry;
import org.webbeans.core.registry.Registry;
import org.webbeans.options.WbPreferences;
import org.webbeans.ui.Component;
import org.webbeans.ui.Resource;
import org.webbeans.ui.UI;

/**
 * Factories for the standard kinds of actions and the bridge to the registry, as NetBeans'
 * {@code org.openide.awt.Actions}.
 */
public final class Actions {

    private static final Logger LOG = Logger.getLogger(Actions.class.getName());
    static final String ACTIONS_ROOT = "Actions";
    /** Entry attribute of the delegate class (an {@link ActionListener}); set by the annotation processor. */
    static final String ATTR_DELEGATE = "delegate";
    static final String ATTR_TYPE = "type";
    static final String ATTR_SELECTION = "selectionType";
    static final String ATTR_KIND = "kind";
    static final String ATTR_KEY = "key";
    static final String KIND_CALLBACK = "callback";
    static final String KIND_OPEN_TC = "openTopComponent";

    private static final Map<Entry, Action> SPECIAL = new WeakHashMap<Entry, Action>();

    private Actions() {
    }

    // ---- factories -----------------------------------------------------------------------------------------

    /** An action enabled regardless of the selection. */
    public static Action alwaysEnabled(final ActionListener delegate, String displayName, Resource icon, boolean noIconInMenu) {
        SimpleAction a = new SimpleAction(new Supplier<ActionListener>() {
            @Override
            public ActionListener get() {
                return delegate;
            }
        }, null);
        a.putValue(Action.NAME, displayName);
        if (icon != null) {
            a.putValue(Action.ICON, icon);
        }
        if (noIconInMenu) {
            a.putValue(Action.NO_ICON_IN_MENU, Boolean.TRUE);
        }
        return a;
    }

    /** Convenience for code: a named always-enabled action. */
    public static Action alwaysEnabled(String displayName, ActionListener delegate) {
        return alwaysEnabled(delegate, displayName, null, false);
    }

    /**
     * A context action over {@code type}: enabled when the context holds exactly one ({@code single}) or at
     * least one instance; the performer receives the instances.
     */
    public static <T> ContextAwareAction context(Class<T> type, boolean single, boolean surviveFocusChange,
            final ContextPerformer<T> performer, String displayName, Resource icon) {
        ContextAction<T> a = new ContextAction<T>(type, single, surviveFocusChange, new ContextAction.Performer<T>() {
            @Override
            public void perform(List<? extends T> context, ActionEvent e) {
                performer.perform(context, e);
            }

            @Override
            public boolean enabled(List<? extends T> context) {
                return performer.isEnabled(context);
            }
        }, null);
        a.putValue(Action.NAME, displayName);
        if (icon != null) {
            a.putValue(Action.ICON, icon);
        }
        return a;
    }

    /** What a context action does; {@link #isEnabled} may narrow enablement further (default: enabled). */
    public interface ContextPerformer<T> {
        void perform(List<? extends T> context, ActionEvent e);

        default boolean isEnabled(List<? extends T> context) {
            return true;
        }
    }

    /**
     * A callback action: delegates to the {@link ActionMap} entry {@code key} of the context (the activated
     * window), or to {@code fallback} when the map has none.
     */
    public static ContextAwareAction callback(String key, Action fallback, boolean surviveFocusChange, String displayName, Resource icon) {
        CallbackAction a = new CallbackAction(key, fallback, surviveFocusChange, null);
        a.putValue(Action.NAME, displayName);
        if (icon != null) {
            a.putValue(Action.ICON, icon);
        }
        return a;
    }

    /** A check item bound to a boolean preference of the current user ({@link WbPreferences#forModule}). */
    public static Action checkbox(final Class<?> module, final String key, boolean defaultValue, ActionListener onToggle, String displayName) {
        CheckboxAction a = new CheckboxAction(new Supplier<Preferences>() {
            @Override
            public Preferences get() {
                return WbPreferences.forModule(module);
            }
        }, key, defaultValue, onToggle, null);
        a.putValue(Action.NAME, displayName);
        return a;
    }

    // ---- registry ------------------------------------------------------------------------------------------

    /** The registered action {@code Actions/<category>/<id>} ({@link ActionID}), or null if absent or not visible to the user. */
    public static Action forID(String category, String id) {
        Entry e = Registry.getDefault().entry(ACTIONS_ROOT + "/" + category + "/" + id.replace('.', '-'));
        return e == null ? null : actionFor(e);
    }

    /**
     * The actions of a registry folder in order, {@code null} for separators (role-filtered for the user):
     * what a node's {@code getActions} returns for a {@code <Type>/Actions} folder.
     */
    public static List<Action> forPath(String folderPath) {
        List<Action> out = new ArrayList<Action>();
        boolean pendingSeparator = false;
        for (Entry e : Registry.getDefault().folder(folderPath).getEntries()) {
            if (e.getKind() == Entry.Kind.SEPARATOR) {
                pendingSeparator = !out.isEmpty();
                continue;
            }
            Action a = actionFor(e);
            if (a == null) {
                continue;
            }
            if (pendingSeparator) {
                out.add(null);
                pendingSeparator = false;
            }
            out.add(a);
        }
        return out;
    }

    /**
     * The action behind a registry entry (following references): the instance for {@code Action} entries, an
     * open-window action for {@code Actions/Window} entries, else null (logged).
     */
    public static Action actionFor(Entry e) {
        Entry target = e.getTarget();
        if (target == null) {
            return null;
        }
        if (KIND_OPEN_TC.equals(target.getString(ATTR_KIND))) {
            synchronized (SPECIAL) {
                Action a = SPECIAL.get(target);
                if (a == null) {
                    a = new OpenTopComponentAction(target);
                    SPECIAL.put(target, a);
                }
                return a;
            }
        }
        Class<?> declared = target.getInstanceOf();
        if (declared != null && !Action.class.isAssignableFrom(declared) && !ActionListener.class.isAssignableFrom(declared)) {
            return null;
        }
        Action a = target.getInstance(Action.class);
        if (a == null) {
            ActionListener l = target.getInstance(ActionListener.class);
            if (l != null) {
                synchronized (SPECIAL) {
                    a = SPECIAL.get(target);
                    if (a == null) {
                        a = alwaysEnabled(l, target.getDisplayName(), null, false);
                        SPECIAL.put(target, a);
                    }
                }
            }
        }
        if (a == null) {
            LOG.log(Level.FINE, "Registry entry {0} is not an action", e.getPath());
        }
        return a;
    }

    /**
     * Entry factory used by the annotation processor for {@link ActionListener} classes: builds the action
     * from the entry's attributes ({@code delegate}, {@code type}, {@code selectionType}, {@code key}, ...).
     * The delegate class is loaded here, i.e. on first use of the entry.
     */
    public static Action fromEntry(Entry e) {
        String delegate = e.getString(ATTR_DELEGATE);
        String kind = e.getString(ATTR_KIND);
        final Class<?> cls = delegate == null || delegate.isEmpty() ? null : load(e, delegate);
        boolean survive = Boolean.TRUE.equals(e.getAttributes().get("surviveFocusChange"));
        if (KIND_CALLBACK.equals(kind)) {
            Action fallback = cls == null ? null : instantiateAction(cls);
            return new CallbackAction(e.getString(ATTR_KEY), fallback, survive, e);
        }
        if (cls == null) {
            throw new IllegalStateException("Action entry " + e.getPath() + " has no delegate class");
        }
        String type = e.getString(ATTR_TYPE);
        if (type == null || type.isEmpty()) {
            return new SimpleAction(new Supplier<ActionListener>() {
                @Override
                public ActionListener get() {
                    return (ActionListener) newInstance(cls);
                }
            }, e);
        }
        Class<?> contextType = load(e, type);
        boolean single = !"ANY".equals(e.getString(ATTR_SELECTION));
        return contextAction(contextType, single, survive, cls, e);
    }

    private static <T> Action contextAction(final Class<T> contextType, final boolean single, boolean survive, final Class<?> cls, Entry e) {
        return new ContextAction<T>(contextType, single, survive, new ContextAction.Performer<T>() {
            @Override
            public void perform(List<? extends T> context, ActionEvent ev) {
                ActionListener l = single ? (ActionListener) newInstance(cls, contextType, context.get(0))
                        : (ActionListener) newInstance(cls, List.class, new ArrayList<T>(context));
                l.actionPerformed(ev);
            }

            @Override
            public boolean enabled(List<? extends T> context) {
                return true;
            }
        }, e);
    }

    private static Class<?> load(Entry e, String name) {
        ClassLoader l = e.getClassLoader() != null ? e.getClassLoader() : Lookup.getDefault().lookup(ClassLoader.class);
        if (l == null) {
            l = Actions.class.getClassLoader();
        }
        try {
            return Class.forName(name, true, l);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Cannot load " + name + " for " + e.getPath(), ex);
        }
    }

    private static Action instantiateAction(Class<?> cls) {
        Object o = newInstance(cls);
        if (o instanceof Action) {
            return (Action) o;
        }
        if (o instanceof ActionListener) {
            return alwaysEnabled((ActionListener) o, null, null, false);
        }
        return null;
    }

    private static Object newInstance(Class<?> cls) {
        try {
            Constructor<?> c = cls.getConstructor();
            return c.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot instantiate " + cls.getName(), ex);
        }
    }

    private static Object newInstance(Class<?> cls, Class<?> paramType, Object arg) {
        try {
            for (Constructor<?> c : cls.getConstructors()) {
                if (c.getParameterCount() == 1 && c.getParameterTypes()[0].isAssignableFrom(paramType)) {
                    return c.newInstance(arg);
                }
            }
            throw new IllegalStateException("No public constructor (" + paramType.getName() + ") in " + cls.getName());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot instantiate " + cls.getName(), ex);
        }
    }

    // ---- invocation ------------------------------------------------------------------------------------------

    /**
     * Invokes an action from a presenter: context-aware templates are bound to the context first, failures are
     * logged and reported as an error notification instead of killing the event task.
     */
    public static void invoke(Action action, Component source, Lookup context) {
        if (action == null) {
            return;
        }
        ActionEvent e = new ActionEvent(source, context);
        try {
            action.actionPerformed(e);
        } catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "Action " + action.getDisplayName() + " failed", ex);
            UI ui = e.getUI();
            if (ui != null) {
                org.webbeans.notifications.NotificationDisplayer.getDefault(ui.getSession()).notify(
                        action.getDisplayName(), null, String.valueOf(ex.getMessage() != null ? ex.getMessage() : ex),
                        null, org.webbeans.notifications.NotificationDisplayer.Priority.HIGH,
                        org.webbeans.notifications.NotificationDisplayer.Category.ERROR);
            }
        }
    }

    /** The text a menu shows for the action: {@link Action#MENU_TEXT} if set, else the display name. */
    public static String menuText(Action a) {
        Object t = a.getValue(Action.MENU_TEXT);
        return t != null ? t.toString() : a.getDisplayName();
    }

    /** The text a popup shows for the action: {@link Action#POPUP_TEXT} if set, else the display name. */
    public static String popupText(Action a) {
        Object t = a.getValue(Action.POPUP_TEXT);
        return t != null ? t.toString() : a.getDisplayName();
    }

    /**
     * Builds a {@link Popup} for the actions ({@code null} = separator) over the context: attached to the UI's
     * overlay and shown at the given page coordinates; it detaches itself when closed or when an item ran.
     */
    public static Popup actionsToPopup(List<Action> actions, Lookup context, int x, int y) {
        Popup p = new Popup(context);
        boolean pendingSeparator = false;
        for (Action a : actions) {
            if (a == null) {
                pendingSeparator = p.getChildCount() > 0;
                continue;
            }
            Component item;
            if (a instanceof Presenter.Popup) {
                item = ((Presenter.Popup) a).getPopupPresenter();
            } else {
                item = ActionPresenters.popupItem(a, context);
            }
            if (item == null) {
                continue;
            }
            if (pendingSeparator) {
                p.add(new Separator());
                pendingSeparator = false;
            }
            p.add(item);
        }
        if (p.getChildCount() > 0) {
            p.show(x, y);
        }
        return p;
    }

    /** Array variant of {@link #actionsToPopup(List, Lookup, int, int)}. */
    public static Popup actionsToPopup(Action[] actions, Lookup context, int x, int y) {
        return actionsToPopup(java.util.Arrays.asList(actions), context, x, y);
    }
}
