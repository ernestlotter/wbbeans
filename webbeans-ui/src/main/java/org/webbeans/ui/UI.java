package org.webbeans.ui;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ProxyLookup;
import org.webbeans.core.json.Json;
import org.webbeans.core.registry.Renderer;

/**
 * One browser tab: a component tree, the delta buffer and history, the transport, and timers. All mutations
 * happen on the session executor; a {@link #flush()} after every task turns the buffered deltas into one message.
 */
public final class UI implements Lookup.Provider {

    private static final Logger LOG = Logger.getLogger(UI.class.getName());
    static final ThreadLocal<UI> CURRENT = new ThreadLocal<UI>();

    public enum State { INIT, ATTACHED, DETACHED, CLOSED }

    /** A cancellable scheduled task. */
    public interface Timer {
        void cancel();

        boolean isCancelled();
    }

    /** The root container ({@code wb.Root}): hosts the application's content and the overlay layer. */
    @Renderer(id = "wb.Root", resource = "org/webbeans/ui/client/renderers/root.js")
    public static final class Root extends Container {
        public static final Property<String> TITLE = Property.of("title", String.class, "WebBeans");
        public static final Property<String> THEME = Property.of("theme", String.class, "system");
        public static final Property<Map<String, Object>> KEYMAP = Property.of("keymap", mapClass(), null);
        /** A bound key was pressed: data {@code key} (e.g. {@code Ctrl+Shift+S}) and {@code action}. */
        public static final EventType<Event> SHORTCUT = EventType.of("shortcut", Event::new);

        Root() {
            super("wb.Root");
        }

        @SuppressWarnings("unchecked")
        private static Class<Map<String, Object>> mapClass() {
            return (Class<Map<String, Object>>) (Class<?>) Map.class;
        }
    }

    private final UISession session;
    private final String id;
    private final Root root = new Root();
    private final Map<Integer, Component> components = new LinkedHashMap<Integer, Component>();
    private final Map<Object, Object> attributes = new java.util.concurrent.ConcurrentHashMap<Object, Object>();
    private final DeltaBuffer buffer = new DeltaBuffer();
    private final DeltaHistory history = new DeltaHistory(64, 2 * 1024 * 1024);
    private final InstanceContent content = new InstanceContent();
    private final Lookup lookup;
    private final Set<Timer> timers = new HashSet<Timer>();
    private final List<Runnable> closeListeners = new ArrayList<Runnable>();
    private int nextId = 1;
    private long seq;
    private long lastEventSeq;
    private volatile Transport transport;
    private volatile State state = State.INIT;
    private volatile Instant lastActivity = Instant.now();
    private volatile Instant detachedAt;
    private boolean initialised;
    private final List<Runnable> beforeFlush = new ArrayList<Runnable>();
    private final List<Component> modals = new ArrayList<Component>();
    private final Set<Component> modalExempt = new HashSet<Component>();
    private final Map<String, Runnable> shortcuts = new LinkedHashMap<String, Runnable>();
    private final Map<String, Object> keymapActions = new LinkedHashMap<String, Object>();
    private Registration shortcutListener;

    UI(UISession session, String id) {
        this.session = session;
        this.id = id;
        content.add(this);
        this.lookup = new ProxyLookup(new AbstractLookup(content), session.getLookup());
        attach(root, null, 0);
    }

    /** The UI whose task the current thread is running, or null. */
    public static UI current() {
        return CURRENT.get();
    }

    public String getId() {
        return id;
    }

    public UISession getSession() {
        return session;
    }

    /** Per-tab attributes (any thread): services keep their per-UI state here instead of in static maps. */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(Class<T> key) {
        return (T) attributes.get(key);
    }

    public <T> void setAttribute(Class<T> key, T value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    public Root getRoot() {
        return root;
    }

    public State getState() {
        return state;
    }

    public Instant getLastActivity() {
        return lastActivity;
    }

    public Instant getDetachedAt() {
        return detachedAt;
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    /** Adds an object to this UI's lookup (e.g. the window manager). */
    public void addToLookup(Object o) {
        content.add(o);
    }

    public void removeFromLookup(Object o) {
        content.remove(o);
    }

    /** Sets the page content (replaces any previous content). */
    public void setContent(Component c) {
        checkThread();
        root.removeAll();
        root.add(c, "content");
    }

    public Component getContent() {
        for (Component c : root.getChildren()) {
            if ("content".equals(c.getConstraint())) {
                return c;
            }
        }
        return null;
    }

    public void setTitle(String title) {
        root.set(Root.TITLE, title);
    }

    /**
     * Binds a keyboard shortcut for this tab: {@code key} is {@code [Ctrl+][Alt+][Shift+]<Key>} with the key as the
     * browser names it ({@code S}, {@code F5}, {@code Tab}, {@code Enter}); Ctrl stands for Cmd on macOS. The client
     * swallows the key and sends a {@code shortcut} event; the runnable executes on the session executor.
     * Rebinding a key replaces the previous binding.
     */
    public Registration bindShortcut(final String key, final String actionId, final Runnable action) {
        checkThread();
        if (shortcutListener == null) {
            shortcutListener = root.addListener(Root.SHORTCUT, new Listener<Event>() {
                @Override
                public void onEvent(Event e) {
                    Runnable r = shortcuts.get(e.getString("key"));
                    if (r != null) {
                        r.run();
                    }
                }
            });
        }
        shortcuts.put(key, action);
        keymapActions.put(key, actionId);
        root.set(Root.KEYMAP, new LinkedHashMap<String, Object>(keymapActions));
        return new Registration() {
            @Override
            public void remove() {
                if (shortcuts.get(key) == action) {
                    shortcuts.remove(key);
                    keymapActions.remove(key);
                    Map<String, Object> m = new LinkedHashMap<String, Object>(keymapActions);
                    root.set(Root.KEYMAP, m);
                }
            }
        };
    }

    /** Sends the browser tab to another URL (absolute, or relative to the application). */
    public void navigate(String url) {
        root.call("navigate", url);
    }

    /** Reloads the page in this tab; the UI is closed and a fresh one created by the next page load. */
    public void reload() {
        root.call("reload");
    }

    /**
     * Ends the user's HTTP session from this tab: the client posts to {@code /logout} and shows the login page.
     * Every other tab of the same session loses its connection and is redirected too.
     */
    public void logout() {
        root.call("logout");
    }

    // ---- modality ----------------------------------------------------------------------------------------------

    /**
     * Declares a modal component (a dialog): until {@link #popModal} the UI drops client events from components
     * outside the newest modal one, except those registered with {@link #allowWhileModal} (popups, toasts).
     * The client blocks input visually; this is the server-side guard against a stale or malicious client.
     */
    public void pushModal(Component c) {
        checkThread();
        modals.remove(c);
        modals.add(c);
    }

    public void popModal(Component c) {
        checkThread();
        modals.remove(c);
    }

    /** The newest modal component, or null. */
    public Component getModal() {
        return modals.isEmpty() ? null : modals.get(modals.size() - 1);
    }

    /** Lets a transient component (a popup menu, a notification) receive events while a modal one is open. */
    public void allowWhileModal(Component c) {
        modalExempt.add(c);
    }

    private boolean acceptsEvent(Component c) {
        Component top = getModal();
        if (top == null || !top.isAttached()) {
            if (top != null) {
                modals.remove(top);
                return acceptsEvent(c);
            }
            return true;
        }
        for (Component p = c; p != null; p = p.getParent()) {
            if (p == top || modalExempt.contains(p)) {
                return true;
            }
        }
        return false;
    }

    public Component findComponent(int componentId) {
        return components.get(componentId);
    }

    public int getComponentCount() {
        return components.size();
    }

    // ---- threading ---------------------------------------------------------------------------------------------

    /** Runs the task on the session executor with this UI current; deltas are flushed afterwards. */
    public Future<?> access(Runnable task) {
        return session.access(task, this);
    }

    void checkThread() {
        if (UISession.isStrictThreading() && !session.isSessionThread()) {
            throw new IllegalStateException("Component access outside the session executor; use ui.access(...)");
        }
    }

    public Timer schedule(final Runnable task, long delayMs) {
        final TimerImpl t = new TimerImpl();
        t.future = UISession.scheduler().schedule(new Runnable() {
            @Override
            public void run() {
                synchronized (timers) {
                    timers.remove(t);
                }
                if (!t.cancelled && state != State.CLOSED) {
                    access(task);
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS);
        synchronized (timers) {
            timers.add(t);
        }
        return t;
    }

    public Timer scheduleAtFixedRate(final Runnable task, long initialDelayMs, long periodMs) {
        final TimerImpl t = new TimerImpl();
        t.future = UISession.scheduler().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (!t.cancelled && state != State.CLOSED) {
                    access(task);
                } else {
                    t.cancel();
                }
            }
        }, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
        synchronized (timers) {
            timers.add(t);
        }
        return t;
    }

    private final class TimerImpl implements Timer {
        volatile ScheduledFuture<?> future;
        volatile boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
            if (future != null) {
                future.cancel(false);
            }
            synchronized (timers) {
                timers.remove(this);
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    // ---- tree maintenance (called by Container) -------------------------------------------------------------------

    DeltaBuffer deltas() {
        return buffer;
    }

    void attach(Component c, Component parent, int index) {
        register(c, parent, index);
        // attached() hooks run once the whole subtree is created, so calls they make follow the create ops
        notifyAttached(c);
    }

    private void register(Component c, Component parent, int index) {
        c.attachTo(this, nextId++);
        components.put(c.getId(), c);
        buffer.create(c, parent, index);
        for (Object[] call : c.drainPendingCalls()) {
            buffer.call(c, (String) call[0], (Object[]) call[1]);
        }
        if (c instanceof Container) {
            int i = 0;
            for (Component child : ((Container) c).getChildren()) {
                register(child, c, i++);
            }
        }
    }

    private void notifyAttached(Component c) {
        c.fireAttached();
        if (c instanceof Container) {
            for (Component child : new ArrayList<Component>(((Container) c).getChildren())) {
                if (child.getUI() == this) {
                    notifyAttached(child);
                }
            }
        }
    }

    void detach(Component c) {
        buffer.remove(c);
        forget(c);
    }

    private void forget(Component c) {
        if (c instanceof Container) {
            for (Component child : ((Container) c).getChildren()) {
                forget(child);
            }
        }
        components.remove(c.getId());
        modals.remove(c);
        modalExempt.remove(c);
        c.detachFrom();
    }

    // ---- protocol ------------------------------------------------------------------------------------------------

    /**
     * Turns buffered deltas into one message (recorded in the history) and sends it if a transport is attached.
     * Called by the session after every task.
     */
    public void flush() {
        if (state == State.CLOSED) {
            return;
        }
        runBeforeFlush();
        if (buffer.isEmpty()) {
            return;
        }
        List<Map<String, Object>> ops = buffer.drain();
        Map<String, Object> msg = new LinkedHashMap<String, Object>();
        msg.put("t", "delta");
        msg.put("seq", ++seq);
        msg.put("ops", ops);
        String json = Json.write(msg);
        history.add(seq, json);
        Transport t = transport;
        if (t != null) {
            t.send(json);
        }
    }

    /**
     * Runs a task once, just before the next message is assembled: components that coalesce many mutations
     * into one {@code call} (charts) register here on the first mutation of a task and emit the merged call
     * when the flush comes. Session executor only.
     */
    public void beforeFlush(Runnable r) {
        checkThread();
        beforeFlush.add(r);
    }

    private void runBeforeFlush() {
        while (!beforeFlush.isEmpty()) {
            List<Runnable> rs = new ArrayList<Runnable>(beforeFlush);
            beforeFlush.clear();
            for (Runnable r : rs) {
                try {
                    r.run();
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "beforeFlush task failed", e);
                }
            }
        }
    }

    /** The full tree as an {@code init} message; also resets the buffer and history. */
    private String snapshot() {
        runBeforeFlush(); // lets the registered components reset their state; what they emit is superseded by resync()
        buffer.clear();
        history.clear();
        List<Map<String, Object>> ops = new ArrayList<Map<String, Object>>();
        DeltaBuffer tmp = new DeltaBuffer();
        snapshot(root, null, 0, tmp);
        ops.addAll(tmp.drain());
        // components whose client state was delivered by calls re-send it; those calls follow the creates
        for (Component c : new ArrayList<Component>(components.values())) {
            try {
                c.resync();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "resync failed for " + c, e);
            }
        }
        ops.addAll(buffer.drain());
        Map<String, Object> msg = new LinkedHashMap<String, Object>();
        msg.put("t", "init");
        msg.put("seq", ++seq);
        msg.put("uiId", id);
        msg.put("locale", session.getLocale().toLanguageTag());
        msg.put("ops", ops);
        String json = Json.write(msg);
        history.add(seq, json);
        return json;
    }

    private void snapshot(Component c, Component parent, int index, DeltaBuffer into) {
        into.create(c, parent, index);
        if (c instanceof Container) {
            int i = 0;
            for (Component child : ((Container) c).getChildren()) {
                snapshot(child, c, i++, into);
            }
        }
    }

    /**
     * Binds a transport (first connect or reconnect). On the session executor. {@code lastSeq} is the last message
     * the client applied, or -1 for a fresh client: missing messages are replayed from the history, or a full
     * {@code init} is sent when that is not possible. The first connect also runs the {@link UIProvider}.
     */
    public void connect(Transport t, long lastSeq) {
        checkThread();
        if (state == State.CLOSED) {
            throw new IllegalStateException("UI closed");
        }
        Transport old = transport;
        if (old != null && old != t) {
            old.close();
        }
        transport = t;
        state = State.ATTACHED;
        detachedAt = null;
        touch();
        if (!initialised) {
            initialised = true;
            UIProvider provider = Lookup.getDefault().lookup(UIProvider.class);
            if (provider != null) {
                try {
                    provider.init(this);
                } catch (RuntimeException e) {
                    LOG.log(Level.SEVERE, "UIProvider failed", e);
                }
            } else {
                LOG.log(Level.WARNING, "No UIProvider registered; the page stays empty");
            }
            t.send(snapshot());
            return;
        }
        flush(); // anything buffered while detached becomes a message in the history first
        List<String> replay = lastSeq >= 0 ? history.since(lastSeq, seq) : null;
        if (replay == null) {
            t.send(snapshot());
        } else {
            for (String m : replay) {
                t.send(m);
            }
        }
    }

    /** The transport went away; deltas keep accumulating in the history for a later {@link #connect}. */
    public void disconnect(Transport t) {
        if (transport == t) {
            transport = null;
            if (state == State.ATTACHED) {
                state = State.DETACHED;
                detachedAt = Instant.now();
            }
        }
    }

    public boolean isAttached() {
        return state == State.ATTACHED;
    }

    public long getSeq() {
        return seq;
    }

    /**
     * Handles an upstream message's events on the session executor: deduplicates by client sequence, drops events
     * for unknown components and unlistened types, dispatches the rest.
     */
    @SuppressWarnings("unchecked")
    public void handleEvents(List<Object> events) {
        checkThread();
        touch();
        for (Object o : events) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> ev = (Map<String, Object>) o;
            long s = Json.integer(ev, "s", -1);
            if (s >= 0) {
                if (s <= lastEventSeq) {
                    continue; // duplicate after a reconnect
                }
                lastEventSeq = s;
            }
            int cid = (int) Json.integer(ev, "id", 0);
            String type = Json.string(ev, "type");
            Component c = components.get(cid);
            if (c == null || type == null) {
                LOG.log(Level.FINE, "Event for unknown component {0}", cid);
                continue;
            }
            if (!acceptsEvent(c)) {
                LOG.log(Level.FINE, "Event {0} on {1} dropped: a modal dialog is open", new Object[] {type, c});
                continue;
            }
            Map<String, Object> data = Json.object(ev, "data");
            try {
                if (!c.dispatch(type, data != null ? data : new LinkedHashMap<String, Object>())) {
                    LOG.log(Level.FINE, "Unlistened event {0} on {1}", new Object[] {type, c});
                }
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Event handler failed: " + type + " on " + c, e);
            }
        }
    }

    public void touch() {
        lastActivity = Instant.now();
        session.touch();
    }

    // ---- lifecycle --------------------------------------------------------------------------------------------------

    public void addCloseListener(Runnable r) {
        synchronized (closeListeners) {
            closeListeners.add(r);
        }
    }

    /** Closes this tab's UI: timers cancelled, components detached, transport closed. Safe from any thread. */
    public void close() {
        if (state == State.CLOSED) {
            return;
        }
        access(new Runnable() {
            @Override
            public void run() {
                closeInternal();
            }
        });
    }

    void closeInternal() {
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        List<Timer> ts;
        synchronized (timers) {
            ts = new ArrayList<Timer>(timers);
        }
        for (Timer t : ts) {
            t.cancel();
        }
        try {
            root.removeAll();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Error detaching components", e);
        }
        Transport t = transport;
        transport = null;
        if (t != null) {
            t.close();
        }
        session.removeUI(this);
        List<Runnable> ls;
        synchronized (closeListeners) {
            ls = new ArrayList<Runnable>(closeListeners);
        }
        for (Runnable r : ls) {
            try {
                r.run();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Close listener failed", e);
            }
        }
    }

    @Override
    public String toString() {
        return "UI[" + id + " " + state + "]";
    }
}
