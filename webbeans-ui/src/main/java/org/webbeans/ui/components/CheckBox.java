package org.webbeans.ui.components;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Property;
import org.webbeans.ui.ValueComponent;

@Renderer(id = "wb.CheckBox", resource = "org/webbeans/ui/client/renderers/checkbox.js")
public class CheckBox extends ValueComponent<Boolean> {

    public static final Property<Boolean> SELECTED = Property.of("value", Boolean.class, Boolean.FALSE).clientOwned();
    public static final Property<String> TEXT = Property.of("text", String.class, "");
    public static final Property<Boolean> INDETERMINATE = Property.of("indeterminate", Boolean.class, Boolean.FALSE);

    public CheckBox() {
        super("wb.CheckBox", SELECTED);
    }

    public CheckBox(String text) {
        this();
        setText(text);
    }

    public CheckBox(String text, boolean selected) {
        this(text);
        setSelected(selected);
    }

    @Override
    protected Boolean fromClient(Object raw) {
        return Boolean.TRUE.equals(raw) || "true".equals(String.valueOf(raw));
    }

    public CheckBox setText(String text) {
        set(TEXT, text);
        return this;
    }

    public boolean isSelected() {
        return Boolean.TRUE.equals(getValue());
    }

    public CheckBox setSelected(boolean b) {
        setValue(b);
        return this;
    }

    public CheckBox setIndeterminate(boolean b) {
        set(INDETERMINATE, b);
        return this;
    }
}
