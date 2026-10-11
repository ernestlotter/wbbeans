package org.webbeans.ui.components;

import java.time.LocalDateTime;
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
 * A date-and-time input (native {@code datetime-local}; ISO-8601 on the wire, no zone — the value is a
 * {@link LocalDateTime} in whatever zone the application decides).
 */
@Renderer(id = "wb.DateTimePicker", resource = "org/webbeans/ui/client/renderers/datepicker.js")
public class DateTimePicker extends ValueComponent<LocalDateTime> {

    public static final Property<LocalDateTime> VALUE = Property.of("value", LocalDateTime.class, null).clientOwned();
    public static final Property<LocalDateTime> MIN = Property.of("min", LocalDateTime.class, null);
    public static final Property<LocalDateTime> MAX = Property.of("max", LocalDateTime.class, null);
    public static final Property<Boolean> SECONDS = Property.of("seconds", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> CLEAR_BUTTON = Property.of("clearButton", Boolean.class, Boolean.FALSE);

    public DateTimePicker() {
        super("wb.DateTimePicker", VALUE);
    }

    public DateTimePicker(LocalDateTime value) {
        this();
        setValue(value);
    }

    @Override
    protected LocalDateTime fromClient(Object raw) {
        if (raw == null || "".equals(raw)) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.toString());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(Bundles.getMessage(ValueComponent.class, "ERR_NotADate"));
        }
    }

    @Override
    protected String validateRange(LocalDateTime v) {
        if (v == null) {
            return null;
        }
        LocalDateTime min = get(MIN), max = get(MAX);
        if (min != null && v.isBefore(min)) {
            return Bundles.getMessage(ValueComponent.class, "ERR_DateMin", format(min));
        }
        if (max != null && v.isAfter(max)) {
            return Bundles.getMessage(ValueComponent.class, "ERR_DateMax", format(max));
        }
        return null;
    }

    public static String format(LocalDateTime d) {
        Locale l = LocaleContext.get();
        return d.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(l != null ? l : Locale.getDefault()));
    }

    /** ISO strings without zone; seconds dropped unless {@link #SECONDS}. */
    @Override
    protected Object encodeProperty(String name, Object v) {
        if (v instanceof LocalDateTime) {
            LocalDateTime t = (LocalDateTime) v;
            return get(SECONDS) ? t.withNano(0).toString() : t.withSecond(0).withNano(0).toString();
        }
        return v;
    }

    public DateTimePicker setMin(LocalDateTime d) {
        set(MIN, d);
        return this;
    }

    public DateTimePicker setMax(LocalDateTime d) {
        set(MAX, d);
        return this;
    }

    public DateTimePicker setSeconds(boolean b) {
        set(SECONDS, b);
        return this;
    }

    public DateTimePicker setClearButton(boolean b) {
        set(CLEAR_BUTTON, b);
        return this;
    }

    public void focus() {
        call("focus");
    }
}
