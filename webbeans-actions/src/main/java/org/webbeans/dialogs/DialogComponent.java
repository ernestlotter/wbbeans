package org.webbeans.dialogs;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Container;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Size;
import org.webbeans.ui.UI;

/**
 * The dialog window ({@code wb.Dialog}): title bar with close button, a content slot (constraint
 * {@link #CONTENT}), an optional side slot ({@link #SIDE}, wizard steps), a notification line and a button bar
 * (constraint {@link #BUTTON}, {@link org.webbeans.ui.components.Button}s; {@link #HELP} for a Help button at its left). Modal dialogs darken the page and
 * block input client-side; the server drops events outside them ({@link UI#pushModal}). Draggable by its
 * title, {@code Escape} sends {@link #CLOSE}, {@code Enter} presses the default button.
 */
@Renderer(id = "wb.Dialog", resource = "org/webbeans/actions/client/dialog.js", css = "org/webbeans/actions/client/actions.css")
public class DialogComponent extends Container {

    public static final String CONTENT = "content";
    public static final String SIDE = "side";
    public static final String BUTTON = "button";
    /** A Help button, placed at the left of the button bar; {@code F1} presses it. */
    public static final String HELP = "help";

    public static final Property<String> TITLE = Property.of("title", String.class, "");
    public static final Property<Boolean> MODAL = Property.of("modal", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> CLOSABLE = Property.of("closable", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> RESIZABLE = Property.of("resizable", Boolean.class, Boolean.TRUE);
    /** The icon kind of a message dialog: {@code error}, {@code warning}, {@code info}, {@code question} or null. */
    public static final Property<String> MESSAGE_TYPE = Property.of("messageType", String.class, null);
    /** The notification line text, and its kind ({@code info}/{@code warning}/{@code error}). */
    public static final Property<String> NOTIFICATION = Property.of("notification", String.class, null);
    public static final Property<String> NOTIFICATION_KIND = Property.of("notificationKind", String.class, null);
    /** The user dismissed the dialog (Escape or the close button). */
    public static final EventType<Event> CLOSE = EventType.of("close", Event::new);

    public DialogComponent() {
        super("wb.Dialog");
    }

    public DialogComponent setTitle(String title) {
        set(TITLE, title == null ? "" : title);
        return this;
    }

    public String getTitle() {
        return get(TITLE);
    }

    public DialogComponent setModal(boolean b) {
        set(MODAL, b);
        return this;
    }

    public boolean isModal() {
        return get(MODAL);
    }

    public DialogComponent setClosable(boolean b) {
        set(CLOSABLE, b);
        return this;
    }

    public DialogComponent setResizable(boolean b) {
        set(RESIZABLE, b);
        return this;
    }

    public DialogComponent setMessageType(String kind) {
        set(MESSAGE_TYPE, kind);
        return this;
    }

    public DialogComponent setNotification(String kind, String text) {
        set(NOTIFICATION_KIND, text == null ? null : kind);
        set(NOTIFICATION, text);
        return this;
    }

    public DialogComponent setContent(Component c) {
        Component old = getSlot(CONTENT);
        if (old != null) {
            remove(old);
        }
        if (c != null) {
            add(c, CONTENT, 0);
        }
        return this;
    }

    public Component getSlot(String constraint) {
        for (Component c : getChildren()) {
            if (constraint.equals(c.getConstraint())) {
                return c;
            }
        }
        return null;
    }

    public DialogComponent addButton(org.webbeans.ui.components.Button b) {
        add(b, BUTTON);
        return this;
    }

    public org.webbeans.ui.Registration addCloseListener(Listener<Event> l) {
        return addListener(CLOSE, l);
    }

    /** Opens the dialog in the UI's overlay layer. */
    public void open(UI ui) {
        if (!isAttached()) {
            ui.getRoot().add(this, "overlay");
        }
        if (isModal()) {
            ui.pushModal(this);
        }
        call("open");
    }

    /** Removes the dialog from the page. */
    public void close() {
        UI ui = getUI();
        if (ui != null) {
            ui.popModal(this);
            ((Container) getParent()).remove(this);
        }
    }

    /** Sizes the dialog (default: fits the content). */
    public DialogComponent setSize(Size width, Size height) {
        setWidth(width);
        setHeight(height);
        return this;
    }
}
