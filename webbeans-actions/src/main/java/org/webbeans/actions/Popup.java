package org.webbeans.actions;

import org.openide.util.Lookup;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Container;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.UI;

/**
 * A context menu ({@code wb.Popup}): transient, attached to the UI's overlay layer by {@link #show} and
 * detached when the client reports {@link #CLOSE} (dismissed or an item chosen). Children as in {@link Menu}.
 * Build one with {@link Actions#actionsToPopup}.
 */
@Renderer(id = "wb.Popup", resource = "org/webbeans/actions/client/menu.js", css = "org/webbeans/actions/client/actions.css")
public class Popup extends Container {

    /** Dismissed on the client (click outside, Escape, or after an item ran). */
    public static final EventType<Event> CLOSE = EventType.of("close", Event::new);

    private final Lookup context;
    private Runnable onClose;

    public Popup() {
        this(Lookup.EMPTY);
    }

    public Popup(Lookup context) {
        super("wb.Popup");
        this.context = context;
        addListener(CLOSE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                close();
            }
        });
    }

    /** The lookup the popup's actions are invoked with. */
    public Lookup getContext() {
        return context;
    }

    public Popup setOnClose(Runnable r) {
        this.onClose = r;
        return this;
    }

    /** Attaches the popup to the current UI (if not attached) and opens it at page coordinates. */
    public void show(int x, int y) {
        if (!isAttached()) {
            UI ui = UI.current();
            if (ui == null) {
                throw new IllegalStateException("Popup.show needs a current UI");
            }
            ui.allowWhileModal(this);
            ui.getRoot().add(this, "overlay");
        }
        call("show", x, y);
    }

    /** Detaches the popup. */
    public void close() {
        if (isAttached()) {
            ((Container) getParent()).remove(this);
        }
        if (onClose != null) {
            Runnable r = onClose;
            onClose = null;
            r.run();
        }
    }
}
