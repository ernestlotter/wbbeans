package org.webbeans.ui.components;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Container;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;

/**
 * A floating panel anchored to another component (a rich tooltip, a small form under a button). Add it anywhere
 * in the tree (it is positioned over everything), set the {@link #setAnchor anchor} and {@link #open} it; it
 * closes on Escape or a click outside unless {@link #setCloseOnOutsideClick} says otherwise.
 */
@Renderer(id = "wb.Popover", resource = "org/webbeans/ui/client/renderers/widgets.js", css = "org/webbeans/ui/client/renderers/widgets.css")
public class Popover extends Container {

    public enum Placement { BOTTOM, TOP, LEFT, RIGHT }

    public static final Property<Component> ANCHOR = Property.of("anchor", Component.class, null);
    public static final Property<Boolean> OPEN = Property.of("open", Boolean.class, Boolean.FALSE).clientOwned();
    public static final Property<Placement> PLACEMENT = Property.of("placement", Placement.class, Placement.BOTTOM);
    public static final Property<Boolean> CLOSE_ON_OUTSIDE = Property.of("closeOnOutside", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> ARROW = Property.of("arrow", Boolean.class, Boolean.TRUE);
    public static final Property<Integer> PADDING = Property.of("padding", Integer.class, Integer.valueOf(10));

    public static final EventType<Event> CLOSE = EventType.of("close", Event::new);

    public Popover() {
        super("wb.Popover");
        addListener(CLOSE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                setFromClient(OPEN, Boolean.FALSE);
            }
        });
    }

    public Popover(Component anchor, Component content) {
        this();
        setAnchor(anchor);
        add(content);
    }

    public Popover setAnchor(Component c) {
        set(ANCHOR, c);
        return this;
    }

    public Popover setPlacement(Placement p) {
        set(PLACEMENT, p);
        return this;
    }

    public Popover setCloseOnOutsideClick(boolean b) {
        set(CLOSE_ON_OUTSIDE, b);
        return this;
    }

    public Popover setArrow(boolean b) {
        set(ARROW, b);
        return this;
    }

    public Popover setPadding(int px) {
        set(PADDING, px);
        return this;
    }

    public Popover open() {
        set(OPEN, Boolean.TRUE);
        return this;
    }

    public Popover close() {
        set(OPEN, Boolean.FALSE);
        return this;
    }

    public Popover toggle() {
        return isOpen() ? close() : open();
    }

    public boolean isOpen() {
        return Boolean.TRUE.equals(get(OPEN));
    }

    /** Fires when the user dismissed it (Escape, outside click). */
    public Registration addCloseListener(Listener<Event> l) {
        return addListener(CLOSE, l);
    }
}
