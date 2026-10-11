package org.webbeans.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openide.util.Lookup;

/**
 * Base class of everything rendered in the browser. A component has typed {@link Property properties}, an
 * optional parent, listeners for the {@link EventType events} its renderer can send, and a renderer id
 * ({@code "wb.Button"}) naming the client code that draws it.
 *
 * <p>Property changes and calls are recorded as deltas on the owning {@link UI} once the component is attached
 * (reachable from the UI root); until then they are kept and sent with the {@code create} op. All access
 * happens on the session executor (see {@link UI#access}).
 */
public abstract class Component implements Lookup.Provider {

    public static final Property<Boolean> VISIBLE = Property.of("visible", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> ENABLED = Property.of("enabled", Boolean.class, Boolean.TRUE);
    public static final Property<String> TOOLTIP = Property.of("tooltip", String.class, null);
    public static final Property<String> STYLE_NAME = Property.of("styleName", String.class, null);
    public static final Property<Size> WIDTH = Property.of("width", Size.class, null);
    public static final Property<Size> HEIGHT = Property.of("height", Size.class, null);
    public static final Property<String> ID_FOR_TEST = Property.of("testId", String.class, null);

    public static final EventType<ClickEvent> CLICK = EventType.of("click", ClickEvent::new);
    public static final EventType<Event> CONTEXT_MENU = EventType.of("contextMenu", Event::new);
    public static final EventType<Event> FOCUS = EventType.of("focus", Event::new);
    public static final EventType<Event> BLUR = EventType.of("blur", Event::new);

    private final String rendererId;
    private final Map<String, Object> props = new LinkedHashMap<String, Object>();
    private final Map<String, List<Listener<? extends Event>>> listeners = new LinkedHashMap<String, List<Listener<? extends Event>>>();
    private final Map<String, EventType<?>> eventTypes = new LinkedHashMap<String, EventType<?>>();
    private List<Object[]> pendingCalls;
    private Component parent;
    private Object constraint;
    private UI ui;
    private int id;
    private Lookup lookup;

    protected Component(String rendererId) {
        if (rendererId == null || rendererId.isEmpty()) {
            throw new IllegalArgumentException("rendererId");
        }
        this.rendererId = rendererId;
    }

    public final String getRendererId() {
        return rendererId;
    }

    /** UI-unique id while attached, else 0. */
    public final int getId() {
        return id;
    }

    /** The owning UI while attached, else null. */
    public final UI getUI() {
        return ui;
    }

    public final boolean isAttached() {
        return ui != null;
    }

    public final Component getParent() {
        return parent;
    }

    /** The layout constraint given when added to the parent, or null. */
    public final Object getConstraint() {
        return constraint;
    }

    // ---- properties ------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public final <T> T get(Property<T> p) {
        return props.containsKey(p.getName()) ? (T) props.get(p.getName()) : p.getDefault();
    }

    public final <T> Component set(Property<T> p, T value) {
        checkThread();
        Object old = props.containsKey(p.getName()) ? props.get(p.getName()) : p.getDefault();
        if (old == value || (old != null && old.equals(value))) {
            return this;
        }
        props.put(p.getName(), value);
        if (ui != null) {
            ui.deltas().set(this, p.getName(), encodeProperty(p.getName(), value));
        }
        return this;
    }

    /** Re-sends a property's current value even though it did not change (to overrule a client-side edit). */
    protected final <T> void resend(Property<T> p) {
        checkThread();
        if (ui != null) {
            ui.deltas().set(this, p.getName(), encodeProperty(p.getName(), get(p)));
        }
    }

    /** Hook to translate a property value into what the client should see (default: the value itself). */
    protected Object encodeProperty(String name, Object value) {
        return value;
    }

    /** Stores a value the client reported for a client-owned property: no delta is sent back. */
    protected final <T> void setFromClient(Property<T> p, T value) {
        props.put(p.getName(), value);
    }

    /** All property values that were set, by name, encoded for the client (used for the create op). */
    final Map<String, Object> propertyValues() {
        Map<String, Object> out = new LinkedHashMap<String, Object>(props.size());
        for (Map.Entry<String, Object> e : props.entrySet()) {
            out.put(e.getKey(), encodeProperty(e.getKey(), e.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    public Component setVisible(boolean b) {
        return set(VISIBLE, b);
    }

    public boolean isVisible() {
        return get(VISIBLE);
    }

    public Component setEnabled(boolean b) {
        return set(ENABLED, b);
    }

    public boolean isEnabled() {
        return get(ENABLED);
    }

    public Component setTooltip(String t) {
        return set(TOOLTIP, t);
    }

    public Component setStyleName(String css) {
        return set(STYLE_NAME, css);
    }

    public Component setWidth(Size s) {
        return set(WIDTH, s);
    }

    public Component setHeight(Size s) {
        return set(HEIGHT, s);
    }

    public Component setTestId(String id) {
        return set(ID_FOR_TEST, id);
    }

    // ---- events ----------------------------------------------------------------------------------------------

    public final <E extends Event> Registration addListener(final EventType<E> type, final Listener<E> listener) {
        checkThread();
        List<Listener<? extends Event>> list = listeners.get(type.getName());
        boolean first = list == null;
        if (first) {
            list = new ArrayList<Listener<? extends Event>>();
            listeners.put(type.getName(), list);
            eventTypes.put(type.getName(), type);
            if (ui != null) {
                ui.deltas().listen(this, type);
            }
        }
        list.add(listener);
        final List<Listener<? extends Event>> l = list;
        return new Registration() {
            @Override
            public void remove() {
                l.remove(listener);
            }
        };
    }

    public Registration addClickListener(Listener<ClickEvent> l) {
        return addListener(CLICK, l);
    }

    /** Whether the server wants this event type (the client only sends listened types). */
    public final boolean isListened(String eventName) {
        return listeners.containsKey(eventName);
    }

    /** Fires an event from the server side, as if the client had sent it: listeners of the type run now. */
    protected final void fireEvent(EventType<?> type, Map<String, Object> data) {
        checkThread();
        dispatch(type.getName(), data);
    }

    /** The listened event types, for the create op. */
    final List<EventType<?>> listenedEvents() {
        return new ArrayList<EventType<?>>(eventTypes.values());
    }

    /** Dispatches a client event: builds the typed event and calls listeners. Called by the UI on the executor. */
    @SuppressWarnings("unchecked")
    final boolean dispatch(String eventName, Map<String, Object> data) {
        EventType<?> type = eventTypes.get(eventName);
        if (type == null) {
            return false;
        }
        Event event = type.create(this, data);
        onEvent(event);
        for (Listener<? extends Event> l : new ArrayList<Listener<? extends Event>>(listeners.get(eventName))) {
            ((Listener<Event>) l).onEvent(event);
        }
        return true;
    }

    /** Hook for subclasses that need to see events before listeners (client-owned property updates). */
    protected void onEvent(Event event) {
    }

    /**
     * Called when the UI sends a full {@code init} (first connect or resync after a lost history): a component
     * whose client-side state was delivered through {@link #call} re-sends it here, because the client starts
     * from the properties alone. Default: nothing.
     */
    protected void resync() {
    }

    // ---- client calls ----------------------------------------------------------------------------------------

    /** Invokes a method of the renderer on the client; no result. Queued until attached. */
    public final void call(String method, Object... args) {
        checkThread();
        if (ui != null) {
            ui.deltas().call(this, method, args);
        } else {
            if (pendingCalls == null) {
                pendingCalls = new ArrayList<Object[]>();
            }
            pendingCalls.add(new Object[] {method, args});
        }
    }

    final List<Object[]> drainPendingCalls() {
        List<Object[]> l = pendingCalls;
        pendingCalls = null;
        return l == null ? Collections.<Object[]>emptyList() : l;
    }

    // ---- lookup ----------------------------------------------------------------------------------------------

    /** The component's lookup: its own contents (see {@link #associateLookup}) over its parent's, ending at the UI's. */
    @Override
    public Lookup getLookup() {
        Lookup own = lookup != null ? lookup : Lookup.EMPTY;
        Lookup outer = parent != null ? parent.getLookup() : ui != null ? ui.getLookup() : Lookup.EMPTY;
        return new org.openide.util.lookup.ProxyLookup(own, outer);
    }

    protected final void associateLookup(Lookup l) {
        this.lookup = l;
    }

    // ---- attach / detach (package private, driven by Container and UI) -----------------------------------------

    final void setParent(Component parent, Object constraint) {
        this.parent = parent;
        this.constraint = constraint;
    }

    final void attachTo(UI ui, int id) {
        this.ui = ui;
        this.id = id;
    }

    final void fireAttached() {
        attached();
    }

    final void detachFrom() {
        detached();
        this.ui = null;
        this.id = 0;
    }

    /** Called after the component became part of an attached tree. */
    protected void attached() {
    }

    /** Called before the component leaves an attached tree. */
    protected void detached() {
    }

    protected final void checkThread() {
        if (ui != null) {
            ui.checkThread();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "#" + id;
    }
}
