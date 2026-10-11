package org.webbeans.ui.components;


import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;
import org.webbeans.ui.Resource;

/** A compact token (a filter, a tag, a selected item) with an optional icon and close button; clickable and selectable. */
@Renderer(id = "wb.Chip", resource = "org/webbeans/ui/client/renderers/widgets.js", css = "org/webbeans/ui/client/renderers/widgets.css")
public class Chip extends Component {

    public static final Property<String> TEXT = Property.of("text", String.class, "");
    public static final Property<Resource> ICON = Property.of("icon", Resource.class, null);
    public static final Property<Boolean> CLOSABLE = Property.of("closable", Boolean.class, Boolean.FALSE);
    /** Clicking toggles {@link #SELECTED} (a filter chip). */
    public static final Property<Boolean> SELECTABLE = Property.of("selectable", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> SELECTED = Property.of("selected", Boolean.class, Boolean.FALSE).clientOwned();
    public static final Property<String> COLOR = Property.of("color", String.class, null);

    public static final EventType<Event> CLOSE = EventType.of("close", Event::new);
    public static final EventType<Event> SELECT = EventType.of("select", Event::new);

    public Chip() {
        super("wb.Chip");
        addListener(SELECT, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                setFromClient(SELECTED, e.getBoolean("value"));
            }
        });
    }

    public Chip(String text) {
        this();
        setText(text);
    }

    public Chip setText(String t) {
        set(TEXT, t);
        return this;
    }

    public Chip setIcon(Resource r) {
        set(ICON, r);
        return this;
    }

    public Chip setClosable(boolean b) {
        set(CLOSABLE, b);
        return this;
    }

    public Chip setSelectable(boolean b) {
        set(SELECTABLE, b);
        return this;
    }

    public Chip setSelected(boolean b) {
        set(SELECTED, b);
        return this;
    }

    public boolean isSelected() {
        return Boolean.TRUE.equals(get(SELECTED));
    }

    /** A CSS colour for the chip's tint. */
    public Chip setColor(String css) {
        set(COLOR, css);
        return this;
    }

    public Registration addCloseListener(Listener<Event> l) {
        return addListener(CLOSE, l);
    }

    /** Fires after the selection toggled (the new state is {@link #isSelected()}). */
    public Registration addSelectListener(Listener<Event> l) {
        return addListener(SELECT, l);
    }

}
