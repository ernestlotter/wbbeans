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
 * A scrolling viewport around one child. The scroll offsets are client-owned (reported, debounced, by
 * {@link #SCROLL}); {@link #scrollTo} and {@link #scrollToBottom} move the viewport from the server.
 * {@code Panel.setScrollable(true)} is the lighter alternative when the offsets do not matter.
 */
@Renderer(id = "wb.ScrollPane", resource = "org/webbeans/ui/client/renderers/scrollpane.js")
public class ScrollPane extends Container {

    public enum Policy { AUTO, ALWAYS, NEVER }

    public static final Property<Integer> SCROLL_X = Property.of("scrollX", Integer.class, Integer.valueOf(0)).clientOwned();
    public static final Property<Integer> SCROLL_Y = Property.of("scrollY", Integer.class, Integer.valueOf(0)).clientOwned();
    public static final Property<Policy> HORIZONTAL = Property.of("horizontal", Policy.class, Policy.AUTO);
    public static final Property<Policy> VERTICAL = Property.of("vertical", Policy.class, Policy.AUTO);

    /** The viewport scrolled (debounced): {@code x}, {@code y}, {@code atBottom}. */
    public static final EventType<Event> SCROLL = EventType.of("scroll", Event::new).lazy(150);

    public ScrollPane() {
        super("wb.ScrollPane");
        addListener(SCROLL, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                setFromClient(SCROLL_X, e.getInt("x", 0));
                setFromClient(SCROLL_Y, e.getInt("y", 0));
            }
        });
    }

    public ScrollPane(Component content) {
        this();
        setContent(content);
    }

    public ScrollPane setContent(Component c) {
        removeAll();
        add(c);
        return this;
    }

    public Component getContent() {
        return getChildCount() > 0 ? getChildren().get(0) : null;
    }

    public ScrollPane setPolicies(Policy horizontal, Policy vertical) {
        set(HORIZONTAL, horizontal);
        set(VERTICAL, vertical);
        return this;
    }

    public void scrollTo(int x, int y) {
        set(SCROLL_X, x);
        set(SCROLL_Y, y);
        call("scrollTo", x, y);
    }

    public void scrollToBottom() {
        call("scrollToBottom");
    }

    public void scrollIntoView(Component child) {
        if (child != null && child.isAttached()) {
            call("scrollIntoView", child.getId());
        }
    }

    public Registration addScrollListener(Listener<Event> l) {
        return addListener(SCROLL, l);
    }
}
