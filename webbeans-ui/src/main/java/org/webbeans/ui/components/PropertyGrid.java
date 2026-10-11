package org.webbeans.ui.components;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.EnumLabels;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Labeled;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;
import org.webbeans.ui.Resource;
import org.webbeans.ui.table.Cell;

/**
 * A two-column name/value grid over one bean with typed in-place editors — a property sheet for plain objects
 * (the nodes module has {@code PropertySheet} for {@code Node}s). Properties are declared with a getter and an
 * optional setter; the editor follows the type: text, number, checkbox, date, or a choice list (enums
 * automatically, with {@link EnumLabels} labels and icons; any type via {@link Prop#choices}). Properties are
 * grouped by {@link Prop#category category} and a {@link Prop#renderer renderer} can turn a value into a styled
 * {@link Cell}. Edits run the setter on the server and fire {@link #CHANGE}; a setter that throws shows its
 * message at the property.
 */
@Renderer(id = "wb.PropertyGrid", resource = "org/webbeans/ui/client/renderers/propertygrid.js", css = "org/webbeans/ui/client/renderers/propertygrid.css")
public class PropertyGrid<T> extends Component {

    /** A declared property. */
    public final class Prop<V> {
        private final String id;
        private final String label;
        private final Class<V> type;
        private final Function<T, V> getter;
        private final BiConsumer<T, V> setter;
        private String category;
        private String description;
        private boolean readOnly;
        private List<V> choices;
        private Function<V, String> choiceLabel;
        private Function<V, Resource> choiceIcon;
        private Function<V, Cell> renderer;
        private Function<String, V> parser;

        Prop(String id, String label, Class<V> type, Function<T, V> getter, BiConsumer<T, V> setter) {
            this.id = id;
            this.label = label;
            this.type = type;
            this.getter = getter;
            this.setter = setter;
            this.readOnly = setter == null;
            if (type.isEnum()) {
                choices = new ArrayList<V>();
                Collections.addAll(choices, type.getEnumConstants());
            }
        }

        public String getId() {
            return id;
        }

        public Prop<V> category(String c) {
            category = c;
            republish();
            return this;
        }

        public Prop<V> description(String d) {
            description = d;
            republish();
            return this;
        }

        public Prop<V> readOnly() {
            readOnly = true;
            republish();
            return this;
        }

        /** Offers a fixed set of values in a drop-down. */
        public Prop<V> choices(Collection<? extends V> values) {
            choices = new ArrayList<V>(values);
            republish();
            return this;
        }

        public Prop<V> choiceLabel(Function<V, String> f) {
            choiceLabel = f;
            republish();
            return this;
        }

        public Prop<V> choiceIcon(Function<V, Resource> f) {
            choiceIcon = f;
            republish();
            return this;
        }

        /** Renders the value as a styled cell (icon, html, style, tooltip) instead of plain text. */
        public Prop<V> renderer(Function<V, Cell> f) {
            renderer = f;
            republish();
            return this;
        }

        /** Parses typed text for types the grid does not know (which otherwise are read-only). */
        public Prop<V> parser(Function<String, V> f) {
            parser = f;
            republish();
            return this;
        }

        String kind() {
            if (choices != null) {
                return "choice";
            }
            if (type == Boolean.class || type == boolean.class) {
                return "bool";
            }
            if (Number.class.isAssignableFrom(type) || type.isPrimitive() && type != char.class) {
                return "number";
            }
            if (type == LocalDate.class) {
                return "date";
            }
            return "text";
        }

        boolean editable() {
            return !readOnly && setter != null && (parser != null || choices != null || type == String.class || type == Boolean.class
                    || type == boolean.class || type == LocalDate.class || numberType() != null);
        }

        Class<?> numberType() {
            if (type == Integer.class || type == int.class || type == Long.class || type == long.class || type == Double.class
                    || type == double.class || type == Float.class || type == float.class || type == Short.class || type == short.class
                    || type == java.math.BigDecimal.class) {
                return type;
            }
            return null;
        }

        String label(V v) {
            if (v == null) {
                return "";
            }
            if (choiceLabel != null) {
                return choiceLabel.apply(v);
            }
            if (v instanceof Enum) {
                return EnumLabels.label((Enum<?>) v);
            }
            if (v instanceof Labeled) {
                return ((Labeled) v).getDisplayName();
            }
            return String.valueOf(v);
        }

        Resource icon(V v) {
            if (v == null) {
                return null;
            }
            if (choiceIcon != null) {
                return choiceIcon.apply(v);
            }
            if (v instanceof Enum) {
                return EnumLabels.icon((Enum<?>) v);
            }
            if (v instanceof Labeled) {
                return ((Labeled) v).getIcon();
            }
            return null;
        }

        String choiceKey(V v) {
            return v == null ? null : v instanceof Enum ? ((Enum<?>) v).name() : String.valueOf(choices.indexOf(v));
        }

        Map<String, Object> describe() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", id);
            m.put("label", label);
            if (category != null) {
                m.put("cat", category);
            }
            if (description != null) {
                m.put("desc", description);
            }
            m.put("kind", kind());
            if (!editable()) {
                m.put("ro", Boolean.TRUE);
            }
            if (choices != null) {
                List<Object> cs = new ArrayList<Object>();
                for (V c : choices) {
                    Map<String, Object> cm = new LinkedHashMap<String, Object>();
                    cm.put("k", choiceKey(c));
                    cm.put("l", label(c));
                    Resource ic = icon(c);
                    if (ic != null) {
                        cm.put("i", ic);
                    }
                    cs.add(cm);
                }
                m.put("choices", cs);
            }
            return m;
        }

        Map<String, Object> value(T bean) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            V v;
            try {
                v = bean == null ? null : getter.apply(bean);
            } catch (RuntimeException e) {
                m.put("t", "!");
                m.put("tt", String.valueOf(e.getMessage()));
                return m;
            }
            if (renderer != null) {
                Cell c = renderer.apply(v);
                if (c != null) {
                    m.putAll(cellMap(c));
                }
            }
            if (!m.containsKey("t") && !m.containsKey("h")) {
                m.put("t", label(v));
                Resource ic = icon(v);
                if (ic != null) {
                    m.put("i", ic);
                }
            }
            if (choices != null) {
                m.put("k", choiceKey(v));
            } else if (v instanceof Boolean) {
                m.put("v", v);
            } else if (v != null) {
                m.put("v", v instanceof Number ? v : String.valueOf(v));
            }
            return m;
        }

        @SuppressWarnings("unchecked")
        V parse(Object raw) {
            if (choices != null) {
                String k = raw == null ? null : String.valueOf(raw);
                for (V c : choices) {
                    if (choiceKey(c).equals(k)) {
                        return c;
                    }
                }
                return null;
            }
            if (raw == null) {
                return null;
            }
            String s = String.valueOf(raw).trim();
            if (parser != null) {
                return parser.apply(s);
            }
            if (type == String.class) {
                return (V) String.valueOf(raw);
            }
            if (type == Boolean.class || type == boolean.class) {
                return (V) Boolean.valueOf(raw instanceof Boolean ? (Boolean) raw : "true".equalsIgnoreCase(s));
            }
            if (s.isEmpty()) {
                return null;
            }
            if (type == LocalDate.class) {
                return (V) LocalDate.parse(s);
            }
            if (type == Integer.class || type == int.class) {
                return (V) Integer.valueOf((int) Math.round(Double.parseDouble(s)));
            }
            if (type == Long.class || type == long.class) {
                return (V) Long.valueOf(Math.round(Double.parseDouble(s)));
            }
            if (type == Short.class || type == short.class) {
                return (V) Short.valueOf((short) Math.round(Double.parseDouble(s)));
            }
            if (type == Float.class || type == float.class) {
                return (V) Float.valueOf(s);
            }
            if (type == java.math.BigDecimal.class) {
                return (V) new java.math.BigDecimal(s);
            }
            return (V) Double.valueOf(s);
        }
    }

    /** A property was edited: {@link #getPropertyId()}, {@link #getOldValue()}, {@link #getNewValue()}. */
    public static class ChangeEvent extends Event {
        public ChangeEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getPropertyId() {
            return getString("id");
        }

        public Object getOldValue() {
            return ((PropertyGrid<?>) getSource()).lastOld;
        }

        public Object getNewValue() {
            return ((PropertyGrid<?>) getSource()).lastNew;
        }

        @SuppressWarnings("unchecked")
        public <B> B getBean() {
            return (B) ((PropertyGrid<?>) getSource()).getBean();
        }
    }

    @SuppressWarnings("unchecked")
    public static final Property<List<Map<String, Object>>> PROPERTIES = (Property<List<Map<String, Object>>>) (Property<?>) Property.of("properties", List.class, new ArrayList<Object>());
    @SuppressWarnings("unchecked")
    public static final Property<Map<String, Object>> VALUES = (Property<Map<String, Object>>) (Property<?>) Property.of("values", Map.class, new LinkedHashMap<String, Object>());
    public static final Property<Boolean> SHOW_DESCRIPTION = Property.of("showDescription", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> SHOW_CATEGORIES = Property.of("showCategories", Boolean.class, Boolean.TRUE);
    public static final Property<String> EMPTY_TEXT = Property.of("emptyText", String.class, null);

    public static final EventType<Event> EDIT = EventType.of("edit", Event::new);
    public static final EventType<ChangeEvent> CHANGE = EventType.of("change", ChangeEvent::new);

    private final Map<String, Prop<?>> props = new LinkedHashMap<String, Prop<?>>();
    private T bean;
    private boolean publishScheduled;

    public PropertyGrid() {
        super("wb.PropertyGrid");
        addListener(EDIT, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                applyEdit(e.getString("id"), e.getData().get("value"));
            }
        });
    }

    // ---- declaration --------------------------------------------------------------------------------------------

    public <V> Prop<V> addProperty(String id, String label, Class<V> type, Function<T, V> getter, BiConsumer<T, V> setter) {
        Prop<V> p = new Prop<V>(id, label, type, getter, setter);
        props.put(id, p);
        republish();
        return p;
    }

    /** A read-only property. */
    public <V> Prop<V> addProperty(String id, String label, Class<V> type, Function<T, V> getter) {
        return addProperty(id, label, type, getter, null);
    }

    public Prop<?> getProperty(String id) {
        return props.get(id);
    }

    public PropertyGrid<T> removeProperty(String id) {
        props.remove(id);
        republish();
        return this;
    }

    public PropertyGrid<T> setBean(T bean) {
        this.bean = bean;
        refresh();
        return this;
    }

    public T getBean() {
        return bean;
    }

    /** Re-reads every value from the bean. */
    public PropertyGrid<T> refresh() {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        for (Prop<?> p : props.values()) {
            values.put(p.id, p.value(bean));
        }
        set(VALUES, values);
        return this;
    }

    public PropertyGrid<T> setShowDescription(boolean b) {
        set(SHOW_DESCRIPTION, b);
        return this;
    }

    public PropertyGrid<T> setShowCategories(boolean b) {
        set(SHOW_CATEGORIES, b);
        return this;
    }

    public PropertyGrid<T> setEmptyText(String t) {
        set(EMPTY_TEXT, t);
        return this;
    }

    public Registration addChangeListener(Listener<ChangeEvent> l) {
        return addListener(CHANGE, l);
    }

    private void republish() {
        if (publishScheduled) {
            return;
        }
        if (getUI() == null) {
            publish();
            return;
        }
        publishScheduled = true;
        getUI().beforeFlush(new Runnable() {
            @Override
            public void run() {
                publishScheduled = false;
                publish();
            }
        });
    }

    private void publish() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (Prop<?> p : props.values()) {
            list.add(p.describe());
        }
        set(PROPERTIES, list);
        refresh();
    }

    @Override
    protected void attached() {
        if (!publishScheduled) {
            publish();
        }
    }

    // ---- editing ------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private <V> void applyEdit(String id, Object raw) {
        Prop<V> p = (Prop<V>) props.get(id);
        if (p == null || bean == null || !p.editable()) {
            return;
        }
        V old;
        V value;
        try {
            old = p.getter.apply(bean);
            value = p.parse(raw);
            p.setter.accept(bean, value);
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            call("editError", id, msg);
            refresh();
            resend(VALUES); // the map did not change: make the client show the old value again
            return;
        }
        refresh();
        Map<String, Object> d = new LinkedHashMap<String, Object>();
        d.put("id", id);
        lastOld = old;
        lastNew = value;
        fireEvent(CHANGE, d);
    }

    private Object lastOld;
    private Object lastNew;

    static Map<String, Object> cellMap(Cell c) {
        Object j = c.toJson();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        if (j instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) j).entrySet()) {
                m.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return m;
    }
}
