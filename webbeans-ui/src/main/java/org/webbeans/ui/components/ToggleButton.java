package org.webbeans.ui.components;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Property;
import org.webbeans.ui.Resource;
import org.webbeans.ui.ValueComponent;

/** A button that stays pressed; the value is the pressed state. */
@Renderer(id = "wb.ToggleButton", resource = "org/webbeans/ui/client/renderers/widgets.js", css = "org/webbeans/ui/client/renderers/widgets.css")
public class ToggleButton extends ValueComponent<Boolean> {

    public static final Property<Boolean> SELECTED = Property.of("value", Boolean.class, Boolean.FALSE).clientOwned();
    public static final Property<String> TEXT = Property.of("text", String.class, "");
    public static final Property<Resource> ICON = Property.of("icon", Resource.class, null);
    /** An icon shown instead of {@link #ICON} while pressed. */
    public static final Property<Resource> SELECTED_ICON = Property.of("selectedIcon", Resource.class, null);

    public ToggleButton() {
        super("wb.ToggleButton", SELECTED);
    }

    public ToggleButton(String text) {
        this();
        setText(text);
    }

    public ToggleButton(String text, boolean selected) {
        this(text);
        setSelected(selected);
    }

    @Override
    protected Boolean fromClient(Object raw) {
        return Boolean.TRUE.equals(raw) || "true".equals(String.valueOf(raw));
    }

    public ToggleButton setText(String text) {
        set(TEXT, text);
        return this;
    }

    public ToggleButton setIcon(Resource icon) {
        set(ICON, icon);
        return this;
    }

    public ToggleButton setSelectedIcon(Resource icon) {
        set(SELECTED_ICON, icon);
        return this;
    }

    public boolean isSelected() {
        return Boolean.TRUE.equals(getValue());
    }

    public ToggleButton setSelected(boolean b) {
        setValue(b);
        return this;
    }

    public void focus() {
        call("focus");
    }
}
