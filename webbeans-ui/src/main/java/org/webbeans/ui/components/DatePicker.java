package org.webbeans.ui.components;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.Locale;

import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.i18n.LocaleContext;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Property;
import org.webbeans.ui.ValueComponent;

/**
 * A date input (the browser's native picker; the wire format is ISO-8601, the display format the browser's
 * locale). {@link #MIN}/{@link #MAX} bound the picker and are validated on the server.
 */
@Renderer(id = "wb.DatePicker", resource = "org/webbeans/ui/client/renderers/datepicker.js")
public class DatePicker extends ValueComponent<LocalDate> {

    public static final Property<LocalDate> VALUE = Property.of("value", LocalDate.class, null).clientOwned();
    public static final Property<LocalDate> MIN = Property.of("min", LocalDate.class, null);
    public static final Property<LocalDate> MAX = Property.of("max", LocalDate.class, null);
    public static final Property<Boolean> CLEAR_BUTTON = Property.of("clearButton", Boolean.class, Boolean.FALSE);

    public DatePicker() {
        super("wb.DatePicker", VALUE);
    }

    public DatePicker(LocalDate value) {
        this();
        setValue(value);
    }

    @Override
    protected LocalDate fromClient(Object raw) {
        if (raw == null || "".equals(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.toString());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(Bundles.getMessage(ValueComponent.class, "ERR_NotADate"));
        }
    }

    @Override
    protected String validateRange(LocalDate v) {
        if (v == null) {
            return null;
        }
        LocalDate min = get(MIN), max = get(MAX);
        if (min != null && v.isBefore(min)) {
            return Bundles.getMessage(ValueComponent.class, "ERR_DateMin", format(min));
        }
        if (max != null && v.isAfter(max)) {
            return Bundles.getMessage(ValueComponent.class, "ERR_DateMax", format(max));
        }
        return null;
    }

    /** A date in the session locale's medium format (for messages). */
    public static String format(LocalDate d) {
        Locale l = LocaleContext.get();
        return d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(l != null ? l : Locale.getDefault()));
    }

    /** Dates travel as ISO strings. */
    @Override
    protected Object encodeProperty(String name, Object v) {
        return v instanceof LocalDate ? v.toString() : v;
    }

    public DatePicker setMin(LocalDate d) {
        set(MIN, d);
        return this;
    }

    public DatePicker setMax(LocalDate d) {
        set(MAX, d);
        return this;
    }

    public DatePicker setClearButton(boolean b) {
        set(CLEAR_BUTTON, b);
        return this;
    }

    public void focus() {
        call("focus");
    }
}
