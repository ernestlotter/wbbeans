package org.webbeans.ui.components;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.Locale;

import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.i18n.LocaleContext;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Property;
import org.webbeans.ui.ValueComponent;

/** A time-of-day input (the browser's time control, locale formatted). {@link #MIN}/{@link #MAX} are validated on the server. */
@Renderer(id = "wb.TimePicker", resource = "org/webbeans/ui/client/renderers/widgets.js", css = "org/webbeans/ui/client/renderers/widgets.css")
public class TimePicker extends ValueComponent<LocalTime> {

    public static final Property<LocalTime> VALUE = Property.of("value", LocalTime.class, null).clientOwned();
    public static final Property<LocalTime> MIN = Property.of("min", LocalTime.class, null);
    public static final Property<LocalTime> MAX = Property.of("max", LocalTime.class, null);
    /** Minute granularity (also whether seconds are shown: 0 shows seconds). */
    public static final Property<Integer> STEP_MINUTES = Property.of("step", Integer.class, Integer.valueOf(1));

    public TimePicker() {
        super("wb.TimePicker", VALUE);
    }

    public TimePicker(LocalTime value) {
        this();
        setValue(value);
    }

    @Override
    protected LocalTime fromClient(Object raw) {
        if (raw == null || "".equals(raw)) {
            return null;
        }
        try {
            return LocalTime.parse(raw.toString());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(Bundles.getMessage(ValueComponent.class, "ERR_NotATime"));
        }
    }

    @Override
    protected String validateRange(LocalTime v) {
        if (v == null) {
            return null;
        }
        LocalTime min = get(MIN), max = get(MAX);
        if (min != null && v.isBefore(min)) {
            return Bundles.getMessage(ValueComponent.class, "ERR_TimeMin", format(min));
        }
        if (max != null && v.isAfter(max)) {
            return Bundles.getMessage(ValueComponent.class, "ERR_TimeMax", format(max));
        }
        return null;
    }

    public static String format(LocalTime t) {
        Locale l = LocaleContext.get();
        return t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(l != null ? l : Locale.getDefault()));
    }

    @Override
    protected Object encodeProperty(String name, Object v) {
        return v instanceof LocalTime ? v.toString() : v;
    }

    @Override
    public TimePicker setValue(LocalTime v) {
        super.setValue(v);
        return this;
    }

    public TimePicker setMin(LocalTime t) {
        set(MIN, t);
        return this;
    }

    public TimePicker setMax(LocalTime t) {
        set(MAX, t);
        return this;
    }

    public TimePicker setStepMinutes(int minutes) {
        set(STEP_MINUTES, minutes);
        return this;
    }

    public void focus() {
        call("focus");
    }
}
