package org.webbeans.actions;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Container;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Resource;

/**
 * A menu ({@code wb.Menu}): top-level in a {@link MenuBar} or a submenu in a menu or popup. Children are
 * {@link MenuItem}s, {@link Menu}s, {@link Separator}s or any component. Children may be created lazily: the
 * client sends {@link #OPEN} when the user opens the menu and {@link #setOnOpen} fills it on first use.
 */
@Renderer(id = "wb.Menu", resource = "org/webbeans/actions/client/menu.js")
public class Menu extends Container {

    public static final Property<String> TEXT = Property.of("text", String.class, "");
    public static final Property<Resource> ICON = Property.of("icon", Resource.class, null);
    /** The user opened the menu. */
    public static final EventType<Event> OPEN = EventType.of("open", Event::new);

    private Runnable onOpen;
    private boolean filled;

    public Menu() {
        super("wb.Menu");
    }

    public Menu(String text) {
        this();
        setText(text);
    }

    public Menu setText(String text) {
        set(TEXT, text == null ? "" : text);
        return this;
    }

    public String getText() {
        return get(TEXT);
    }

    public Menu setIcon(Resource icon) {
        set(ICON, icon);
        return this;
    }

    /** Runs once, when the user first opens the menu (lazy population); {@link #invalidate} re-arms it. */
    public Menu setOnOpen(Runnable r) {
        this.onOpen = r;
        if (r != null && !isListened(OPEN.getName())) {
            addListener(OPEN, new Listener<Event>() {
                @Override
                public void onEvent(Event e) {
                    fillIfNeeded();
                }
            });
        }
        return this;
    }

    /** Fills the menu now if it has an unfulfilled {@link #setOnOpen} filler. */
    public void fillIfNeeded() {
        if (!filled && onOpen != null) {
            filled = true;
            onOpen.run();
        }
    }

    /** Drops the children; the filler runs again on the next open. */
    public void invalidate() {
        removeAll();
        filled = false;
    }

    public boolean isFilled() {
        return filled;
    }

    public Menu addSeparator() {
        add(new Separator());
        return this;
    }
}
