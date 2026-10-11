package org.webbeans.ui;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.function.Function;

import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.i18n.LocaleContext;

/**
 * How an enum constant is shown to the user. The platform's editors, cells and choice lists all go through
 * {@link #label}, which tries, in order:
 * <ol>
 * <li>{@link Labeled#getDisplayName()} when the enum implements {@link Labeled};</li>
 * <li>a label registered with {@link #register} (a function, for enums you cannot change);</li>
 * <li>the key {@code <SimpleClassName>.<CONSTANT>} in the {@code Bundle.properties} of the enum's package
 *     (localised like every other message: {@code Type.GAS_GIANT=Gas giant}, {@code Bundle_de.properties}…);</li>
 * <li>the prettified constant name: {@code GAS_GIANT} → {@code Gas giant}.</li>
 * </ol>
 * Icons come from {@link Labeled#getIcon()} or a registered icon function.
 */
public final class EnumLabels {

    private static final java.util.Map<Class<?>, Function<Object, String>> LABELS = new java.util.concurrent.ConcurrentHashMap<Class<?>, Function<Object, String>>();
    private static final java.util.Map<Class<?>, Function<Object, Resource>> ICONS = new java.util.concurrent.ConcurrentHashMap<Class<?>, Function<Object, Resource>>();

    private EnumLabels() {
    }

    /** Supplies labels (and optionally icons) for an enum type you do not own. */
    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> void register(Class<E> type, Function<E, String> label, Function<E, Resource> icon) {
        LABELS.put(type, (Function<Object, String>) (Function<?, ?>) label);
        if (icon != null) {
            ICONS.put(type, (Function<Object, Resource>) (Function<?, ?>) icon);
        } else {
            ICONS.remove(type);
        }
    }

    public static <E extends Enum<E>> void register(Class<E> type, Function<E, String> label) {
        register(type, label, null);
    }

    /** The display name of a constant (never null; empty for null). */
    public static String label(Enum<?> value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Labeled) {
            String s = ((Labeled) value).getDisplayName();
            if (s != null) {
                return s;
            }
        }
        Function<Object, String> f = LABELS.get(value.getDeclaringClass());
        if (f != null) {
            String s = f.apply(value);
            if (s != null) {
                return s;
            }
        }
        String fromBundle = fromBundle(value);
        return fromBundle != null ? fromBundle : prettify(value.name());
    }

    /** The icon of a constant, or null. */
    public static Resource icon(Enum<?> value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Labeled) {
            Resource r = ((Labeled) value).getIcon();
            if (r != null) {
                return r;
            }
        }
        Function<Object, Resource> f = ICONS.get(value.getDeclaringClass());
        return f != null ? f.apply(value) : null;
    }

    /** The description of a constant (tooltip), or null. */
    public static String description(Enum<?> value) {
        return value instanceof Labeled ? ((Labeled) value).getDescription() : null;
    }

    /** The constant whose name or label matches the text (case-insensitive), or null. */
    public static <E extends Enum<E>> E parse(Class<E> type, String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        for (E c : type.getEnumConstants()) {
            if (c.name().equals(t)) {
                return c;
            }
        }
        for (E c : type.getEnumConstants()) {
            if (c.name().equalsIgnoreCase(t) || label(c).equalsIgnoreCase(t)) {
                return c;
            }
        }
        return null;
    }

    private static String fromBundle(Enum<?> value) {
        Class<?> type = value.getDeclaringClass();
        String pkg = type.getPackage() == null ? "" : type.getPackage().getName();
        String bundleName = (pkg.isEmpty() ? "" : pkg + ".") + "Bundle";
        Locale l = LocaleContext.get();
        try {
            ResourceBundle b = Bundles.getBundle(type.getClassLoader(), bundleName, l != null ? l : Locale.getDefault());
            String key = type.getSimpleName() + "." + value.name();
            return b.containsKey(key) ? b.getString(key) : null;
        } catch (MissingResourceException e) {
            return null;
        }
    }

    /** {@code GAS_GIANT} → {@code Gas giant}; {@code iceGiant} → {@code Ice giant}. */
    public static String prettify(String constant) {
        if (constant == null || constant.isEmpty()) {
            return "";
        }
        String s = constant.replace('_', ' ').trim();
        if (s.equals(s.toUpperCase(Locale.ROOT))) {
            s = s.toLowerCase(Locale.ROOT);
        } else {
            s = s.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
