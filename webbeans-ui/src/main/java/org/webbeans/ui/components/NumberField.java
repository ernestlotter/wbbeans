package org.webbeans.ui.components;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.i18n.LocaleContext;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Property;
import org.webbeans.ui.ValueComponent;

/**
 * A numeric input. The value is a {@link Double} (null when empty); the client formats and parses it with the
 * session locale's separators ({@link #FORMAT}, compiled on the server from the locale and {@link #setDecimals}),
 * and the server validates {@link #MIN}/{@link #MAX}. Optional spinner buttons step by {@link #STEP}.
 */
@Renderer(id = "wb.NumberField", resource = "org/webbeans/ui/client/renderers/numberfield.js")
public class NumberField extends ValueComponent<Double> {

    public static final Property<Double> VALUE = Property.of("value", Double.class, null).clientOwned();
    public static final Property<Double> MIN = Property.of("min", Double.class, null);
    public static final Property<Double> MAX = Property.of("max", Double.class, null);
    public static final Property<Double> STEP = Property.of("step", Double.class, Double.valueOf(1));
    public static final Property<Integer> DECIMALS = Property.of("decimals", Integer.class, Integer.valueOf(2));
    public static final Property<Boolean> SPINNER = Property.of("spinner", Boolean.class, Boolean.FALSE);
    public static final Property<String> PLACEHOLDER = Property.of("placeholder", String.class, null);
    public static final Property<String> PREFIX = Property.of("prefix", String.class, null);
    public static final Property<String> SUFFIX = Property.of("suffix", String.class, null);
    public static final Property<Boolean> GROUPING = Property.of("grouping", Boolean.class, Boolean.TRUE);
    /** The compiled format spec the client uses: {@code {decimal, grouping, maxFrac, minFrac}}; derived, read-only. */
    @SuppressWarnings("unchecked")
    public static final Property<Map<String, Object>> FORMAT = (Property<Map<String, Object>>) (Property<?>) Property.of("format", Map.class, null);

    private Locale formatLocale;

    public NumberField() {
        super("wb.NumberField", VALUE);
    }

    public NumberField(Double value) {
        this();
        setValue(value);
    }

    @Override
    protected Double fromClient(Object raw) {
        if (raw == null || "".equals(raw)) {
            return null;
        }
        if (raw instanceof Number) {
            return ((Number) raw).doubleValue();
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(Bundles.getMessage(ValueComponent.class, "ERR_NotANumber"));
        }
    }

    @Override
    protected String validateRange(Double v) {
        if (v == null) {
            return null;
        }
        Double min = get(MIN), max = get(MAX);
        if (min != null && v < min) {
            return Bundles.getMessage(ValueComponent.class, "ERR_Min", format(min));
        }
        if (max != null && v > max) {
            return Bundles.getMessage(ValueComponent.class, "ERR_Max", format(max));
        }
        return null;
    }

    /** Formats a number the way the client shows it (session locale, the field's decimals). */
    public String format(double v) {
        NumberFormat f = NumberFormat.getNumberInstance(locale());
        f.setMaximumFractionDigits(get(DECIMALS));
        f.setGroupingUsed(get(GROUPING));
        return f.format(v);
    }

    private Locale locale() {
        Locale l = LocaleContext.get();
        return l != null ? l : Locale.getDefault();
    }

    @Override
    protected void attached() {
        compileFormat();
    }

    /** Recompiles the format spec for the current session locale (called on attach and on a locale change). */
    public void compileFormat() {
        Locale l = locale();
        formatLocale = l;
        DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(l);
        Map<String, Object> spec = new LinkedHashMap<String, Object>();
        spec.put("decimal", String.valueOf(sym.getDecimalSeparator()));
        spec.put("grouping", get(GROUPING) ? String.valueOf(sym.getGroupingSeparator()) : "");
        spec.put("maxFrac", get(DECIMALS));
        spec.put("minFrac", Integer.valueOf(0));
        set(FORMAT, spec);
    }

    /** The locale the current {@link #FORMAT} was compiled for. */
    public Locale getFormatLocale() {
        return formatLocale;
    }

    /** A {@link DecimalFormat} pattern's fraction digits and grouping become the field's settings. */
    public NumberField setPattern(String decimalFormatPattern) {
        DecimalFormat df = new DecimalFormat(decimalFormatPattern);
        setDecimals(df.getMaximumFractionDigits());
        setGrouping(df.isGroupingUsed());
        return this;
    }

    public NumberField setMin(Double min) {
        set(MIN, min);
        return this;
    }

    public NumberField setMax(Double max) {
        set(MAX, max);
        return this;
    }

    public NumberField setRange(double min, double max) {
        return setMin(min).setMax(max);
    }

    public NumberField setStep(double step) {
        set(STEP, step);
        return this;
    }

    public NumberField setDecimals(int digits) {
        set(DECIMALS, digits);
        if (isAttached()) {
            compileFormat();
        }
        return this;
    }

    public NumberField setGrouping(boolean b) {
        set(GROUPING, b);
        if (isAttached()) {
            compileFormat();
        }
        return this;
    }

    public NumberField setSpinner(boolean b) {
        set(SPINNER, b);
        return this;
    }

    public NumberField setPlaceholder(String p) {
        set(PLACEHOLDER, p);
        return this;
    }

    /** Text shown before / after the number inside the field (a currency sign, a unit). */
    public NumberField setPrefix(String p) {
        set(PREFIX, p);
        return this;
    }

    public NumberField setSuffix(String s) {
        set(SUFFIX, s);
        return this;
    }

    public NumberField setIntegerOnly() {
        return setDecimals(0);
    }

    /** The value as an int (0 when empty). */
    public int getIntValue() {
        Double v = getValue();
        return v == null ? 0 : (int) Math.round(v);
    }

    public void focus() {
        call("focus");
    }
}
