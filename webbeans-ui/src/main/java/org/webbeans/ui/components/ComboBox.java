package org.webbeans.ui.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Property;
import org.webbeans.ui.Resource;
import org.webbeans.ui.ValueComponent;

/**
 * A drop-down over domain objects. The client only sees string keys and labels; the value is the selected
 * domain object (null when nothing or an unknown key is selected).
 */
@Renderer(id = "wb.ComboBox", resource = "org/webbeans/ui/client/renderers/combobox.js")
public class ComboBox<T> extends ValueComponent<T> {

    @SuppressWarnings("unchecked")
    private static <T> Property<T> valueProperty() {
        return (Property<T>) Property.of("value", Object.class, null).clientOwned();
    }

    @SuppressWarnings("unchecked")
    private static Property<List<Item>> itemsProperty() {
        return (Property<List<Item>>) (Property<?>) Property.of("items", List.class, new ArrayList<Item>());
    }

    public static final Property<List<Item>> ITEMS = itemsProperty();
    public static final Property<String> PLACEHOLDER = Property.of("placeholder", String.class, null);
    public static final Property<Boolean> FILTERABLE = Property.of("filterable", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> ALLOW_EMPTY = Property.of("allowEmpty", Boolean.class, Boolean.TRUE);

    private final Map<String, T> byKey = new LinkedHashMap<String, T>();
    private Function<T, String> keyFunction = new Function<T, String>() {
        @Override
        public String apply(T t) {
            return String.valueOf(t);
        }
    };
    private Function<T, String> labelFunction = new Function<T, String>() {
        @Override
        public String apply(T t) {
            return t instanceof Enum ? org.webbeans.ui.EnumLabels.label((Enum<?>) t) : t instanceof org.webbeans.ui.Labeled
                    ? ((org.webbeans.ui.Labeled) t).getDisplayName() : String.valueOf(t);   // enums and Labeled values show their display name
        }
    };
    private Function<T, Resource> iconFunction = new Function<T, Resource>() {
        @Override
        public Resource apply(T t) {
            return t instanceof Enum ? org.webbeans.ui.EnumLabels.icon((Enum<?>) t) : t instanceof org.webbeans.ui.Labeled
                    ? ((org.webbeans.ui.Labeled) t).getIcon() : null;
        }
    };

    public ComboBox() {
        super("wb.ComboBox", ComboBox.<T>valueProperty());
    }

    /** How a domain object becomes the key the client uses (default {@code toString}); set before the items. */
    public ComboBox<T> setKeyFunction(Function<T, String> f) {
        this.keyFunction = f;
        return this;
    }

    public ComboBox<T> setItems(Collection<? extends T> items, Function<T, String> label) {
        return setItems(items, label, null);
    }

    public ComboBox<T> setItems(Collection<? extends T> items, Function<T, String> label, Function<T, Resource> icon) {
        this.labelFunction = label;
        if (icon != null) {
            this.iconFunction = icon;
        }
        byKey.clear();
        List<Item> list = new ArrayList<Item>();
        for (T t : items) {
            String key = keyFunction.apply(t);
            byKey.put(key, t);
            list.add(new Item(key, label.apply(t), iconFunction.apply(t), null, true));
        }
        set(ITEMS, list);
        return this;
    }

    public ComboBox<T> setItems(Collection<? extends T> items) {
        return setItems(items, labelFunction, iconFunction);
    }

    public ComboBox<T> setPlaceholder(String p) {
        set(PLACEHOLDER, p);
        return this;
    }

    public ComboBox<T> setFilterable(boolean b) {
        set(FILTERABLE, b);
        return this;
    }

    public ComboBox<T> setAllowEmpty(boolean b) {
        set(ALLOW_EMPTY, b);
        return this;
    }

    @Override
    protected T fromClient(Object raw) {
        if (raw == null || "".equals(raw)) {
            return null;
        }
        T t = byKey.get(String.valueOf(raw));
        if (t == null && !byKey.isEmpty()) {
            throw new IllegalArgumentException("Unknown item");
        }
        return t;
    }

    /** The domain value; the wire carries its key. */
    @Override
    public ComboBox<T> setValue(T v) {
        super.setValue(v);
        return this;
    }

    /** The client sees keys: the value property travels as the item key. */
    @Override
    @SuppressWarnings("unchecked")
    protected Object encodeProperty(String name, Object v) {
        if ("value".equals(name)) {
            return v == null ? null : keyFunction.apply((T) v);
        }
        return v;
    }

    /** Key of the current value as the client sees it. */
    public String getSelectedKey() {
        T v = getValue();
        return v == null ? null : keyFunction.apply(v);
    }
}
