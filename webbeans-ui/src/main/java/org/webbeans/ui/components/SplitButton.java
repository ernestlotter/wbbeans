package org.webbeans.ui.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;
import org.webbeans.ui.Resource;

/**
 * A button with a drop-down part: the main part fires {@code click}, the arrow opens a menu of
 * {@link #addItem items} that fire {@link #SELECT} with the item's key. For a menu of actions see the actions
 * module ({@code DropDownButton}); this one is for a handful of alternatives ("Save", "Save as…", "Save all").
 */
@Renderer(id = "wb.SplitButton", resource = "org/webbeans/ui/client/renderers/widgets.js", css = "org/webbeans/ui/client/renderers/widgets.css")
public class SplitButton extends Component {

    /** An item was chosen: {@link #getKey()}. */
    public static class SelectEvent extends Event {
        public SelectEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getKey() {
            return getString("key");
        }
    }

    public static final Property<String> TEXT = Property.of("text", String.class, "");
    public static final Property<Resource> ICON = Property.of("icon", Resource.class, null);
    public static final Property<Button.Kind> KIND = Property.of("kind", Button.Kind.class, Button.Kind.DEFAULT);
    @SuppressWarnings("unchecked")
    public static final Property<List<Item>> ITEMS = (Property<List<Item>>) (Property<?>) Property.of("items", List.class, new ArrayList<Item>());
    /** Without a main action the whole button opens the menu. */
    public static final Property<Boolean> MENU_ONLY = Property.of("menuOnly", Boolean.class, Boolean.FALSE);

    public static final EventType<SelectEvent> SELECT = EventType.of("select", SelectEvent::new);

    private final List<Item> items = new ArrayList<Item>();

    public SplitButton() {
        super("wb.SplitButton");
    }

    public SplitButton(String text) {
        this();
        setText(text);
    }

    public SplitButton setText(String text) {
        set(TEXT, text);
        return this;
    }

    public SplitButton setIcon(Resource icon) {
        set(ICON, icon);
        return this;
    }

    public SplitButton setKind(Button.Kind kind) {
        set(KIND, kind);
        return this;
    }

    public SplitButton setMenuOnly(boolean b) {
        set(MENU_ONLY, b);
        return this;
    }

    public SplitButton addItem(String key, String label) {
        return addItem(key, label, null);
    }

    public SplitButton addItem(String key, String label, Resource icon) {
        items.add(new Item(key, label, icon, null, true));
        set(ITEMS, new ArrayList<Item>(items));
        return this;
    }

    /** A separator line. */
    public SplitButton addSeparator() {
        items.add(new Item("-", "-", null, null, false));
        set(ITEMS, new ArrayList<Item>(items));
        return this;
    }

    public SplitButton clearItems() {
        items.clear();
        set(ITEMS, new ArrayList<Item>());
        return this;
    }

    public SplitButton onClick(Listener<ClickEvent> l) {
        addClickListener(l);
        return this;
    }

    public Registration addSelectListener(Listener<SelectEvent> l) {
        return addListener(SELECT, l);
    }

    public void focus() {
        call("focus");
    }
}
