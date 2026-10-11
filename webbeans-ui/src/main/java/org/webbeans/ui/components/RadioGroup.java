package org.webbeans.ui.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Property;
import org.webbeans.ui.Resource;
import org.webbeans.ui.ValueComponent;

/**
 * Radio buttons over domain objects, one selected at a time. Like {@link ComboBox} the client sees keys and
 * labels only; the value is the selected object (null when none).
 */
@Renderer(id = "wb.RadioGroup", resource = "org/webbeans/ui/client/renderers/radiogroup.js")
public class RadioGroup<T> extends ValueComponent<T> {

    public enum Orientation { HORIZONTAL, VERTICAL }

    @SuppressWarnings("unchecked")
    private static <T> Property<T> valueProperty() {
        return (Property<T>) Property.of("value", Object.class, null).clientOwned();
    }

    @SuppressWarnings("unchecked")
    public static final Property<List<Item>> ITEMS = (Property<List<Item>>) (Property<?>) Property.of("items", List.class, new ArrayList<Item>());
    public static final Property<Orientation> ORIENTATION = Property.of("orientation", Orientation.class, Orientation.VERTICAL);

    private final Items<T> items = new Items<T>();

    public RadioGroup() {
        super("wb.RadioGroup", RadioGroup.<T>valueProperty());
    }

    public RadioGroup(Collection<? extends T> choices) {
        this();
        setItems(choices);
    }

    public RadioGroup<T> setKeyFunction(Function<T, String> f) {
        items.setKeyFunction(f);
        return this;
    }

    public RadioGroup<T> setItems(Collection<? extends T> objects) {
        set(ITEMS, items.set(objects));
        return this;
    }

    public RadioGroup<T> setItems(Collection<? extends T> objects, Function<T, String> label) {
        items.setLabelFunction(label);
        return setItems(objects);
    }

    public RadioGroup<T> setItems(Collection<? extends T> objects, Function<T, String> label, Function<T, Resource> icon) {
        items.setLabelFunction(label).setIconFunction(icon);
        return setItems(objects);
    }

    /** A second line of text under each choice. */
    public RadioGroup<T> setDescriptions(Function<T, String> description) {
        items.setDescriptionFunction(description);
        return setItems(new ArrayList<T>(items.values()));
    }

    public RadioGroup<T> setOrientation(Orientation o) {
        set(ORIENTATION, o);
        return this;
    }

    @Override
    protected T fromClient(Object raw) {
        if (raw == null || "".equals(raw)) {
            return null;
        }
        T t = items.byKey(raw);
        if (t == null && !items.isEmpty()) {
            throw new IllegalArgumentException(Bundles.getMessage(ValueComponent.class, "ERR_UnknownItem"));
        }
        return t;
    }

    @Override
    public RadioGroup<T> setValue(T v) {
        super.setValue(v);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object encodeProperty(String name, Object v) {
        if ("value".equals(name)) {
            return items.key((T) v);
        }
        return v;
    }

    public String getSelectedKey() {
        return items.key(getValue());
    }
}
