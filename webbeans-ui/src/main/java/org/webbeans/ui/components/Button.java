package org.webbeans.ui.components;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.Component;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Resource;

@Renderer(id = "wb.Button", resource = "org/webbeans/ui/client/renderers/button.js")
public class Button extends Component {

    public enum Kind { DEFAULT, PRIMARY, DANGER, LINK }

    public static final Property<String> TEXT = Property.of("text", String.class, "");
    public static final Property<Resource> ICON = Property.of("icon", Resource.class, null);
    public static final Property<Kind> KIND = Property.of("kind", Kind.class, Kind.DEFAULT);
    public static final Property<Boolean> DEFAULT_BUTTON = Property.of("defaultButton", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> BUSY = Property.of("busy", Boolean.class, Boolean.FALSE);

    public Button() {
        super("wb.Button");
    }

    public Button(String text) {
        this();
        setText(text);
    }

    public Button(String text, Listener<ClickEvent> onClick) {
        this(text);
        addClickListener(onClick);
    }

    public Button setText(String text) {
        set(TEXT, text);
        return this;
    }

    public String getText() {
        return get(TEXT);
    }

    public Button setIcon(Resource icon) {
        set(ICON, icon);
        return this;
    }

    public Button setKind(Kind kind) {
        set(KIND, kind);
        return this;
    }

    public Button setDefaultButton(boolean b) {
        set(DEFAULT_BUTTON, b);
        return this;
    }

    public Button setBusy(boolean b) {
        set(BUSY, b);
        return this;
    }

    public void click() {
        call("click");
    }

    public void focus() {
        call("focus");
    }
}
