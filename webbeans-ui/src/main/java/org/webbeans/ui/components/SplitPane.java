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
 * Two panes with a draggable divider. The first child is the first (left/top) pane, the second child the
 * other; {@link #POSITION} is the first pane's size in pixels and client-owned (the user drags it); a
 * double-click on the divider collapses the first pane when {@link #COLLAPSIBLE}.
 */
@Renderer(id = "wb.SplitPane", resource = "org/webbeans/ui/client/renderers/splitpane.js")
public class SplitPane extends Container {

    public enum Orientation { HORIZONTAL, VERTICAL }

    public static final Property<Orientation> ORIENTATION = Property.of("orientation", Orientation.class, Orientation.HORIZONTAL);
    /** Size of the first pane in px; null = half. */
    public static final Property<Integer> POSITION = Property.of("position", Integer.class, null).clientOwned();
    public static final Property<Integer> MIN_FIRST = Property.of("minFirst", Integer.class, Integer.valueOf(40));
    public static final Property<Integer> MIN_SECOND = Property.of("minSecond", Integer.class, Integer.valueOf(40));
    public static final Property<Boolean> COLLAPSIBLE = Property.of("collapsible", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> COLLAPSED = Property.of("collapsed", Boolean.class, Boolean.FALSE).clientOwned();

    /** The divider moved (debounced); {@link Event#getInt getInt("position")}. */
    public static final EventType<Event> CHANGE = EventType.of("change", Event::new).lazy(200);

    public SplitPane() {
        super("wb.SplitPane");
        addListener(CHANGE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                setFromClient(POSITION, e.getInt("position", 0));
                setFromClient(COLLAPSED, e.getBoolean("collapsed"));
            }
        });
    }

    public SplitPane(Orientation orientation, Component first, Component second) {
        this();
        setOrientation(orientation);
        add(first);
        add(second);
    }

    public SplitPane setOrientation(Orientation o) {
        set(ORIENTATION, o);
        return this;
    }

    public SplitPane setPosition(int px) {
        set(POSITION, px);
        return this;
    }

    public Integer getPosition() {
        return get(POSITION);
    }

    public SplitPane setMinSizes(int first, int second) {
        set(MIN_FIRST, first);
        set(MIN_SECOND, second);
        return this;
    }

    public SplitPane setCollapsible(boolean b) {
        set(COLLAPSIBLE, b);
        return this;
    }

    public SplitPane setCollapsed(boolean b) {
        set(COLLAPSED, b);
        return this;
    }

    public boolean isCollapsed() {
        return get(COLLAPSED);
    }

    public Component getFirst() {
        return getChildCount() > 0 ? getChildren().get(0) : null;
    }

    public Component getSecond() {
        return getChildCount() > 1 ? getChildren().get(1) : null;
    }

    public Registration addChangeListener(Listener<Event> l) {
        return addListener(CHANGE, l);
    }
}
