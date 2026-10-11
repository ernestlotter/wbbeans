package org.webbeans.ui.components;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Property;

/** A small status label or counter ("3", "NEW", "Failed"), coloured by {@link Kind}. */
@Renderer(id = "wb.Badge", resource = "org/webbeans/ui/client/renderers/widgets.js", css = "org/webbeans/ui/client/renderers/widgets.css")
public class Badge extends Component {

    public enum Kind { DEFAULT, INFO, SUCCESS, WARNING, DANGER, ACCENT }

    public static final Property<String> TEXT = Property.of("text", String.class, "");
    public static final Property<Kind> KIND = Property.of("kind", Kind.class, Kind.DEFAULT);
    /** Rounded ends (a pill) instead of a rounded rectangle. */
    public static final Property<Boolean> PILL = Property.of("pill", Boolean.class, Boolean.TRUE);
    /** Just a coloured dot, no text. */
    public static final Property<Boolean> DOT = Property.of("dot", Boolean.class, Boolean.FALSE);

    public Badge() {
        super("wb.Badge");
    }

    public Badge(String text) {
        this();
        setText(text);
    }

    public Badge(String text, Kind kind) {
        this(text);
        setKind(kind);
    }

    public Badge setText(String t) {
        set(TEXT, t);
        return this;
    }

    public Badge setCount(int n) {
        return setText(String.valueOf(n)).setVisible(n != 0);
    }

    public Badge setKind(Kind k) {
        set(KIND, k);
        return this;
    }

    public Badge setPill(boolean b) {
        set(PILL, b);
        return this;
    }

    public Badge setDot(boolean b) {
        set(DOT, b);
        return this;
    }

    @Override
    public Badge setVisible(boolean b) {
        super.setVisible(b);
        return this;
    }
}
