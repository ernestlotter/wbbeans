package org.webbeans.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.function.Function;

import org.webbeans.core.i18n.LocaleContext;
import org.webbeans.ui.EnumLabels;
import org.webbeans.ui.Resource;
import org.webbeans.ui.table.Cell;

/**
 * Default {@link PropertyEditor}s by value type: text, numbers, booleans, enums (as choices labelled through
 * {@link EnumLabels}: {@code Labeled}, a registered label function, the {@code <Enum>.<CONSTANT>} bundle key, or
 * the prettified name); modules register editors for their own types with {@link #register}, and label/icon
 * functions for enums they do not own with {@link #registerEnum} or {@link EnumLabels#register}.
 */
public final class PropertyEditors {

    private static final Map<Class<?>, PropertyEditor<?>> EDITORS = new ConcurrentHashMap<Class<?>, PropertyEditor<?>>();

    private PropertyEditors() {
    }

    public static <T> void register(Class<T> type, PropertyEditor<T> editor) {
        EDITORS.put(type, editor);
    }

    /** Labels (and optionally icons) for an enum you cannot make {@code Labeled}; applies everywhere the enum is shown. */
    public static <E extends Enum<E>> void registerEnum(Class<E> type, Function<E, String> label, Function<E, Resource> icon) {
        EnumLabels.register(type, label, icon);
    }

    /** An enum editor with explicit label and icon functions, for one property ({@code property.setPropertyEditor(...)}). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <E extends Enum<E>> PropertyEditor<E> enumEditor(Class<E> type, final Function<E, String> label, final Function<E, Resource> icon) {
        return (PropertyEditor<E>) rawEnumEditor((Class<Enum>) (Class<?>) type, (Function) label, (Function) icon);
    }

    /** The editor for the type: a registered one, an enum editor, a number/boolean editor, else the text editor. */
    @SuppressWarnings("unchecked")
    public static <T> PropertyEditor<T> forType(Class<T> type) {
        PropertyEditor<?> e = EDITORS.get(type);
        if (e != null) {
            return (PropertyEditor<T>) e;
        }
        if (type.isEnum()) {
            return (PropertyEditor<T>) rawEnumEditor((Class<Enum>) type, null, null);
        }
        if (type == Boolean.class || type == boolean.class) {
            return (PropertyEditor<T>) BOOLEAN;
        }
        if (type == Integer.class || type == int.class) {
            return (PropertyEditor<T>) INTEGER;
        }
        if (type == Long.class || type == long.class) {
            return (PropertyEditor<T>) LONG;
        }
        if (type == Double.class || type == double.class || type == Float.class || type == float.class) {
            return (PropertyEditor<T>) DOUBLE;
        }
        if (type == String.class) {
            return (PropertyEditor<T>) TEXT;
        }
        return (PropertyEditor<T>) OBJECT;
    }

    /** Plain text. */
    public static final PropertyEditor<String> TEXT = new PropertyEditor<String>() {
        @Override
        public Kind getKind() {
            return Kind.TEXT;
        }

        @Override
        public String getAsText(String value) {
            return value == null ? "" : value;
        }

        @Override
        public String fromText(String text) {
            return text;
        }
    };

    /** Any object, displayed with {@code toString}, not editable. */
    public static final PropertyEditor<Object> OBJECT = new PropertyEditor<Object>() {
        @Override
        public Kind getKind() {
            return Kind.TEXT;
        }

        @Override
        public String getAsText(Object value) {
            return value == null ? "" : String.valueOf(value);
        }

        @Override
        public Object fromText(String text) {
            throw new IllegalArgumentException("Not editable");
        }
    };

    public static final PropertyEditor<Boolean> BOOLEAN = new PropertyEditor<Boolean>() {
        @Override
        public Kind getKind() {
            return Kind.BOOLEAN;
        }

        @Override
        public String getAsText(Boolean value) {
            return value == null ? "" : String.valueOf(value);
        }

        @Override
        public Boolean fromText(String text) {
            return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
        }
    };

    public static final PropertyEditor<Integer> INTEGER = new PropertyEditor<Integer>() {
        @Override
        public Kind getKind() {
            return Kind.NUMBER;
        }

        @Override
        public String getAsText(Integer value) {
            return value == null ? "" : java.text.NumberFormat.getIntegerInstance(LocaleContext.get()).format(value);
        }

        @Override
        public Integer fromText(String text) {
            try {
                return Integer.valueOf(text.trim().replace(",", "").replace(" ", ""));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Not a whole number: " + text);
            }
        }

        @Override
        public Map<String, Object> getClientConfig() {
            return Collections.<String, Object>singletonMap("step", 1);
        }
    };

    public static final PropertyEditor<Long> LONG = new PropertyEditor<Long>() {
        @Override
        public Kind getKind() {
            return Kind.NUMBER;
        }

        @Override
        public String getAsText(Long value) {
            return value == null ? "" : java.text.NumberFormat.getIntegerInstance(LocaleContext.get()).format(value);
        }

        @Override
        public Long fromText(String text) {
            try {
                return Long.valueOf(text.trim().replace(",", "").replace(" ", ""));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Not a whole number: " + text);
            }
        }
    };

    public static final PropertyEditor<Double> DOUBLE = new PropertyEditor<Double>() {
        @Override
        public Kind getKind() {
            return Kind.NUMBER;
        }

        @Override
        public String getAsText(Double value) {
            if (value == null) {
                return "";
            }
            java.text.NumberFormat f = java.text.NumberFormat.getNumberInstance(LocaleContext.get());
            f.setMaximumFractionDigits(6);
            return f.format(value);
        }

        @Override
        public Double fromText(String text) {
            try {
                return java.text.NumberFormat.getNumberInstance(LocaleContext.get()).parse(text.trim()).doubleValue();
            } catch (java.text.ParseException e) {
                throw new IllegalArgumentException("Not a number: " + text);
            }
        }
    };

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static PropertyEditor<Enum> rawEnumEditor(final Class<Enum> type, final Function<Enum, String> label, final Function<Enum, Resource> icon) {
        return new PropertyEditor<Enum>() {
            private String labelOf(Enum c) {
                String s = label != null ? label.apply(c) : null;
                return s != null ? s : EnumLabels.label(c);
            }

            private Resource iconOf(Enum c) {
                Resource r = icon != null ? icon.apply(c) : null;
                return r != null ? r : EnumLabels.icon(c);
            }

            @Override
            public Kind getKind() {
                return Kind.CHOICE;
            }

            /** The display label; editing sends labels back, {@link #fromText} accepts labels and names. */
            @Override
            public String getAsText(Enum value) {
                return value == null ? "" : labelOf(value);
            }

            @Override
            public Cell getAsCell(Enum value) {
                if (value == null) {
                    return null;
                }
                Resource r = iconOf(value);
                return r == null ? null : Cell.of(labelOf(value), r);
            }

            @Override
            public Enum fromText(String text) {
                if (text == null || text.trim().isEmpty()) {
                    return null;
                }
                for (Enum c : type.getEnumConstants()) {
                    if (c.name().equals(text) || labelOf(c).equals(text)) {
                        return c;
                    }
                }
                for (Enum c : type.getEnumConstants()) {
                    if (c.name().equalsIgnoreCase(text.trim()) || labelOf(c).equalsIgnoreCase(text.trim())) {
                        return c;
                    }
                }
                throw new IllegalArgumentException("Unknown value: " + text);
            }

            @Override
            public List<Choice<Enum>> getChoices() {
                List<Choice<Enum>> l = new ArrayList<Choice<Enum>>();
                for (Enum c : type.getEnumConstants()) {
                    l.add(new Choice<Enum>(c, labelOf(c), iconOf(c)));
                }
                return l;
            }
        };
    }
}
