package org.webbeans.charts;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Compiles a server-side format pattern into the small spec the client formatter understands, so the browser
 * needs neither {@code DecimalFormat} nor {@code DateTimeFormatter}: numbers → prefix/suffix, fraction digits,
 * grouping, percent and the locale's separators; time → the pattern (the client supports
 * {@code yyyy MM dd HH mm ss SSS MMM EEE}).
 */
public final class FormatSpec {

    private FormatSpec() {
    }

    public static Map<String, Object> compile(String pattern, AxisType axisType, Locale locale) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        if (axisType == AxisType.TIME) {
            m.put("kind", "time");
            m.put("pattern", pattern);
            return m;
        }
        DecimalFormat df = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale == null ? Locale.ROOT : locale));
        DecimalFormatSymbols sym = df.getDecimalFormatSymbols();
        m.put("kind", "number");
        m.put("prefix", df.getPositivePrefix());
        m.put("suffix", df.getPositiveSuffix());
        m.put("minFrac", df.getMinimumFractionDigits());
        m.put("maxFrac", df.getMaximumFractionDigits());
        m.put("grouping", df.isGroupingUsed() && df.getGroupingSize() > 0);
        m.put("groupSize", df.getGroupingSize());
        m.put("multiplier", df.getMultiplier());
        m.put("decimal", String.valueOf(sym.getDecimalSeparator()));
        m.put("group", String.valueOf(sym.getGroupingSeparator()));
        return m;
    }

    /** Server-side formatting with the same pattern (tests and labels). */
    public static String format(String pattern, double value, Locale locale) {
        return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale == null ? Locale.ROOT : locale)).format(value);
    }
}
