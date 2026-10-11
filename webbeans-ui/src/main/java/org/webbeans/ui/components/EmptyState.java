package org.webbeans.ui.components;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.Component;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Resource;

/** The friendly placeholder for an empty list or view: an icon, a title, a line of text and an optional action button. */
@Renderer(id = "wb.EmptyState", resource = "org/webbeans/ui/client/renderers/widgets.js", css = "org/webbeans/ui/client/renderers/widgets.css")
public class EmptyState extends Component {

    public static final Property<Resource> ICON = Property.of("icon", Resource.class, null);
    public static final Property<String> TITLE = Property.of("title", String.class, "");
    public static final Property<String> TEXT = Property.of("text", String.class, null);
    public static final Property<String> ACTION_TEXT = Property.of("actionText", String.class, null);

    public EmptyState() {
        super("wb.EmptyState");
    }

    public EmptyState(String title, String text) {
        this();
        setTitle(title);
        setText(text);
    }

    public EmptyState setIcon(Resource r) {
        set(ICON, r);
        return this;
    }

    public EmptyState setTitle(String t) {
        set(TITLE, t);
        return this;
    }

    public EmptyState setText(String t) {
        set(TEXT, t);
        return this;
    }

    /** A button under the text; its click is the component's {@code click}. */
    public EmptyState setAction(String text, Listener<ClickEvent> onClick) {
        set(ACTION_TEXT, text);
        addClickListener(onClick);
        return this;
    }
}
