package org.webbeans.ui.components;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Property;

/** A hyperlink; without {@link #HREF} it acts as a link-styled button firing {@code click}. */
@Renderer(id = "wb.Link", resource = "org/webbeans/ui/client/renderers/link.js")
public class Link extends Component {

    public static final Property<String> TEXT = Property.of("text", String.class, "");
    public static final Property<String> HREF = Property.of("href", String.class, null);
    public static final Property<Boolean> NEW_TAB = Property.of("newTab", Boolean.class, Boolean.TRUE);

    public Link(String text) {
        super("wb.Link");
        set(TEXT, text);
    }

    public Link(String text, String href) {
        this(text);
        set(HREF, href);
    }

    public Link setText(String text) {
        set(TEXT, text == null ? "" : text);
        return this;
    }

    public Link setHref(String href) {
        set(HREF, href);
        return this;
    }
}
