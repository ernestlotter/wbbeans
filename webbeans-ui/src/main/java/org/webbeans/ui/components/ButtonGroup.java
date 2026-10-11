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
 * A segmented control: one button per choice, joined together, one pressed at a time (a {@link RadioGroup}
 * that looks like a toolbar). Enum choices get their {@link org.webbeans.ui.EnumLabels labels and icons}.
 */
@Renderer(id = "wb.ButtonGroup", resource = "org/webbeans/ui/client/renderers/widgets.js", css = "org/webbeans/ui/client/renderers/widgets.css")
public class ButtonGroup<T> extends ValueComponent<T> {

    @SuppressWarnings("unchecked")
    private static <T> Property<T> valueProperty() {
        return (Property<T>) Property.of("value", Object.class, null).clientOwned();
    }

    @SuppressWarnings("unchecked")
    public static final Property<List<Item>> ITEMS = (Property<List<Item>>) (Property<?>) Property.of("items", List.class, new ArrayList<Item>());
    /** Icon-only buttons (labels become tooltips). */
    public static final Property<Boolean> ICONS_ONLY = Property.of("iconsOnly", Boolean.class, Boolean.FALSE);
    /** Allow clicking the pressed button to clear the selection. */
    public static final Property<Boolean> DESELECTABLE = Property.of("deselectable", Boolean.class, Boolean.FALSE);

    private final Items<T> items = new Items<T>();

    public ButtonGroup() {
        super("wb.ButtonGroup", ButtonGroup.<T>valueProperty());
    }

    public ButtonGroup(Collection<? extends T> choices) {
        this();
        setItems(choices);
    }

    /** All constants of an enum. */
    public static <E extends Enum<E>> ButtonGroup<E> of(Class<E> type) {
        List<E> l = new ArrayList<E>();
        for (E e : type.getEnumConstants()) {
            l.add(e);
        }
        return new ButtonGroup<E>(l);
    }

    public ButtonGroup<T> setKeyFunction(Function<T, String> f) {
        items.setKeyFunction(f);
        return this;
    }

    public ButtonGroup<T> setItems(Collection<? extends T> objects) {
        set(ITEMS, items.set(objects));
        return this;
    }

    public ButtonGroup<T> setItems(Collection<? extends T> objects, Function<T, String> label) {
        items.setLabelFunction(label);
        return setItems(objects);
    }

    public ButtonGroup<T> setItems(Collection<? extends T> objects, Function<T, String> label, Function<T, Resource> icon) {
        items.setLabelFunction(label).setIconFunction(icon);
        return setItems(objects);
    }

    public ButtonGroup<T> setIconsOnly(boolean b) {
        set(ICONS_ONLY, b);
        return this;
    }

    public ButtonGroup<T> setDeselectable(boolean b) {
        set(DESELECTABLE, b);
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
    public ButtonGroup<T> setValue(T v) {
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
