package org.webbeans.windows;

import java.util.Map;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Container;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Property;

/**
 * The docking area: its children are the open {@link TopComponent}s (constraint = component id) and its
 * {@code layout} property is the {@link LayoutModel} tree that tells the client renderer where to show them.
 * Modes and tab groups are not components. All user interactions arrive as semantic events that the
 * {@link WindowManagerImpl} validates against the model before sending the new layout back.
 */
@Renderer(id = "wb.Dock", resource = "org/webbeans/windows/client/dock.js", css = "org/webbeans/windows/client/windows.css")
public final class Dock extends Container {

    public static final Property<Map<String, Object>> LAYOUT = Property.of("layout", mapClass(), null);
    public static final Property<String> ACTIVE = Property.of("active", String.class, null);
    /** mode name → {side, permanent, closeable, floatable, maximizable, displayName}. */
    public static final Property<Map<String, Object>> MODES = Property.of("modes", mapClass(), null);
    /** Localised UI strings of the renderer (tooltips, the empty-mode text). */
    public static final Property<Map<String, Object>> LABELS = Property.of("labels", mapClass(), null);

    /** {mode, tc}: the user clicked a tab. */
    public static final EventType<Event> SELECT = EventType.of("select", Event::new);
    /** {tc}: focus moved into a component. */
    public static final EventType<Event> ACTIVATE = EventType.of("activate", Event::new);
    /** {tc}: the tab's close button. */
    public static final EventType<Event> CLOSE = EventType.of("close", Event::new);
    /** {tc, mode, index} or {tc, beside: mode, side} or {tc, floating: {x,y,w,h}}: a drop. */
    public static final EventType<Event> MOVE = EventType.of("move", Event::new);
    /** {path: [..], sizes: [..]}: a gutter was dragged. */
    public static final EventType<Event> RESIZE = EventType.of("resize", Event::new).lazy(200);
    /** {mode}: toggle maximized. */
    public static final EventType<Event> MAXIMIZE = EventType.of("maximize", Event::new);
    /** {mode, x, y, w, h}: float a mode (or move/resize a floating one). */
    public static final EventType<Event> FLOAT = EventType.of("float", Event::new).lazy(200);
    /** {mode}: dock a floating or minimised mode back. */
    public static final EventType<Event> DOCK = EventType.of("dock", Event::new);
    /** {mode, side}: minimise a mode into a side bar. */
    public static final EventType<Event> MINIMIZE = EventType.of("minimize", Event::new);

    public Dock() {
        super("wb.Dock");
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<String, Object>> mapClass() {
        return (Class<Map<String, Object>>) (Class<?>) Map.class;
    }
}
