package org.webbeans.ui.components;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Property;
import org.webbeans.ui.ValueComponent;

/**
 * Picks a colour: a swatch that opens a palette (and the browser's colour dialog when custom colours are
 * allowed). The value is a {@code #rrggbb} string, lower case, or null.
 */
@Renderer(id = "wb.ColorPicker", resource = "org/webbeans/ui/client/renderers/widgets.js", css = "org/webbeans/ui/client/renderers/widgets.css")
public class ColorPicker extends ValueComponent<String> {

    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    public static final List<String> DEFAULT_PALETTE = Arrays.asList(
            "#c62828", "#ef6c00", "#f9a825", "#2e7d32", "#00838f", "#1565c0", "#4527a0", "#ad1457",
            "#e57373", "#ffb74d", "#fff176", "#81c784", "#4dd0e1", "#64b5f6", "#9575cd", "#f06292",
            "#000000", "#424242", "#757575", "#bdbdbd", "#eeeeee", "#ffffff");

    public static final Property<String> VALUE = Property.of("value", String.class, null).clientOwned();
    @SuppressWarnings("unchecked")
    public static final Property<List<String>> PALETTE = (Property<List<String>>) (Property<?>) Property.of("palette", List.class, DEFAULT_PALETTE);
    public static final Property<Boolean> ALLOW_CUSTOM = Property.of("allowCustom", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> SHOW_TEXT = Property.of("showText", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> CLEARABLE = Property.of("clearable", Boolean.class, Boolean.FALSE);

    public ColorPicker() {
        super("wb.ColorPicker", VALUE);
    }

    public ColorPicker(String color) {
        this();
        setValue(color);
    }

    @Override
    protected String fromClient(Object raw) {
        if (raw == null || "".equals(raw)) {
            return null;
        }
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (!HEX.matcher(s).matches()) {
            throw new IllegalArgumentException(Bundles.getMessage(ValueComponent.class, "ERR_Invalid"));
        }
        return s;
    }

    @Override
    public ColorPicker setValue(String v) {
        super.setValue(v == null ? null : v.toLowerCase(Locale.ROOT));
        return this;
    }

    public ColorPicker setPalette(Collection<String> colors) {
        set(PALETTE, new ArrayList<String>(colors));
        return this;
    }

    public ColorPicker setAllowCustom(boolean b) {
        set(ALLOW_CUSTOM, b);
        return this;
    }

    public ColorPicker setShowText(boolean b) {
        set(SHOW_TEXT, b);
        return this;
    }

    public ColorPicker setClearable(boolean b) {
        set(CLEARABLE, b);
        return this;
    }

    public void focus() {
        call("focus");
    }
}
