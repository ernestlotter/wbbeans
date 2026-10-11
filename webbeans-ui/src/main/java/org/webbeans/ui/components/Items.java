package org.webbeans.ui.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.webbeans.ui.Resource;

/**
 * Maps domain objects to the {@link Item}s a client sees and back: the key function (default {@code toString})
 * identifies an object, the label and icon functions render it. Shared by {@link RadioGroup}, {@link ListBox}
 * and friends; {@link ComboBox} predates it and keeps its own copy of the same logic.
 */
public final class Items<T> {

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
                    ? ((org.webbeans.ui.Labeled) t).getDisplayName() : String.valueOf(t);
        }
    };
    private Function<T, Resource> iconFunction = new Function<T, Resource>() {
        @Override
        public Resource apply(T t) {
            return t instanceof Enum ? org.webbeans.ui.EnumLabels.icon((Enum<?>) t) : t instanceof org.webbeans.ui.Labeled
                    ? ((org.webbeans.ui.Labeled) t).getIcon() : null;
        }
    };
    private Function<T, String> descriptionFunction;
    private List<Item> items = new ArrayList<Item>();

    public Items<T> setKeyFunction(Function<T, String> f) {
        this.keyFunction = f;
        return this;
    }

    public Items<T> setLabelFunction(Function<T, String> f) {
        this.labelFunction = f;
        return this;
    }

    public Items<T> setIconFunction(Function<T, Resource> f) {
        if (f != null) {
            this.iconFunction = f;
        }
        return this;
    }

    public Items<T> setDescriptionFunction(Function<T, String> f) {
        this.descriptionFunction = f;
        return this;
    }

    /** Replaces the objects; returns the items for the client. */
    public List<Item> set(Collection<? extends T> objects) {
        byKey.clear();
        List<Item> list = new ArrayList<Item>();
        for (T t : objects) {
            String key = keyFunction.apply(t);
            byKey.put(key, t);
            list.add(new Item(key, labelFunction.apply(t), iconFunction != null ? iconFunction.apply(t) : null,
                    descriptionFunction != null ? descriptionFunction.apply(t) : null, true));
        }
        items = list;
        return list;
    }

    public List<Item> getItems() {
        return items;
    }

    public String key(T t) {
        return t == null ? null : keyFunction.apply(t);
    }

    public T byKey(Object key) {
        return key == null ? null : byKey.get(String.valueOf(key));
    }

    public boolean contains(Object key) {
        return key != null && byKey.containsKey(String.valueOf(key));
    }

    public boolean isEmpty() {
        return byKey.isEmpty();
    }

    public List<T> objects(Collection<?> keys) {
        List<T> out = new ArrayList<T>();
        if (keys != null) {
            for (Object k : keys) {
                T t = byKey(k);
                if (t != null) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    public List<String> keys(Collection<? extends T> objects) {
        List<String> out = new ArrayList<String>();
        if (objects != null) {
            for (T t : objects) {
                out.add(key(t));
            }
        }
        return out;
    }

    public Collection<T> values() {
        return byKey.values();
    }
}
