package org.webbeans.ui.components;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Property;

/** Multi-line text input. */
@Renderer(id = "wb.TextArea", resource = "org/webbeans/ui/client/renderers/textarea.js")
public class TextArea extends TextField {

    public static final Property<Integer> ROWS = Property.of("rows", Integer.class, 4);
    public static final Property<Boolean> AUTO_GROW = Property.of("autoGrow", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> MONOSPACE = Property.of("monospace", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> WRAP = Property.of("wrap", Boolean.class, Boolean.TRUE);

    public TextArea() {
        super("wb.TextArea", VALUE);
    }

    public TextArea(String value) {
        this();
        setValue(value);
    }

    public TextArea setRows(int rows) {
        set(ROWS, rows);
        return this;
    }

    public TextArea setAutoGrow(boolean b) {
        set(AUTO_GROW, b);
        return this;
    }

    public TextArea setMonospace(boolean b) {
        set(MONOSPACE, b);
        return this;
    }

    public void scrollToLine(int line) {
        call("scrollToLine", line);
    }
}
