package org.webbeans.ui.components;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Property;
import org.webbeans.ui.Resource;

/** Static text with an optional icon. {@link #HTML} accepts a sanitised subset of HTML. */
@Renderer(id = "wb.Label", resource = "org/webbeans/ui/client/renderers/label.js")
public class Label extends Component {

    public static final Property<String> TEXT = Property.of("text", String.class, "");
    public static final Property<String> HTML = Property.of("html", String.class, null);
    public static final Property<Resource> ICON = Property.of("icon", Resource.class, null);
    public static final Property<Boolean> WRAP = Property.of("wrap", Boolean.class, Boolean.TRUE);

    public Label() {
        super("wb.Label");
    }

    public Label(String text) {
        this();
        setText(text);
    }

    public Label setText(String text) {
        set(TEXT, text);
        return this;
    }

    public String getText() {
        return get(TEXT);
    }

    /** Sanitised on the server: only b, i, u, s, code, br, p, ul, ol, li, a[href], span survive. */
    public Label setHtml(String html) {
        set(HTML, Html.sanitize(html));
        return this;
    }

    public Label setIcon(Resource icon) {
        set(ICON, icon);
        return this;
    }
}
