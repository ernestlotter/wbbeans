package org.webbeans.actions;

import org.openide.util.Lookup;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Resource;

/**
 * An entry of a {@link Menu} or {@link Popup} ({@code wb.MenuItem}): text, icon, accelerator, enabled, and for
 * check items a selected state. Usually created by {@link ActionPresenters} and bound to an {@link Action}.
 */
@Renderer(id = "wb.MenuItem", resource = "org/webbeans/actions/client/menu.js")
public class MenuItem extends Component {

    public enum Kind { ITEM, CHECK, RADIO }

    public static final Property<String> TEXT = Property.of("text", String.class, "");
    public static final Property<Resource> ICON = Property.of("icon", Resource.class, null);
    public static final Property<String> ACCELERATOR = Property.of("accelerator", String.class, null);
    public static final Property<Boolean> SELECTED = Property.of("selected", Boolean.class, null);
    public static final Property<Kind> KIND = Property.of("kind", Kind.class, Kind.ITEM);
    /** The user chose the item. */
    public static final EventType<Event> ACTION = EventType.of("action", Event::new);

    private ActionPresenters.Binding binding;

    public MenuItem() {
        super("wb.MenuItem");
    }

    public MenuItem(String text) {
        this();
        setText(text);
    }

    public MenuItem(String text, Listener<Event> onAction) {
        this(text);
        addActionListener(onAction);
    }

    public MenuItem setText(String text) {
        set(TEXT, text == null ? "" : text);
        return this;
    }

    public String getText() {
        return get(TEXT);
    }

    public MenuItem setIcon(Resource icon) {
        set(ICON, icon);
        return this;
    }

    /** Shown right-aligned ({@code Ctrl+S}); informational — the binding is done by the shortcut map. */
    public MenuItem setAccelerator(String key) {
        set(ACCELERATOR, key);
        return this;
    }

    public MenuItem setKind(Kind k) {
        set(KIND, k);
        return this;
    }

    public MenuItem setSelected(Boolean b) {
        set(SELECTED, b);
        return this;
    }

    public Boolean getSelected() {
        return get(SELECTED);
    }

    public org.webbeans.ui.Registration addActionListener(Listener<Event> l) {
        return addListener(ACTION, l);
    }

    /** Binds the item to an action over a context: label, icon, enabled and selected follow the action; choosing the item invokes it. */
    public MenuItem setAction(Action action, Lookup context, boolean popup) {
        if (binding != null) {
            binding.stop();
        }
        binding = action == null ? null : new ActionPresenters.Binding(action, context, this, popup);
        if (binding != null && isAttached()) {
            binding.start();
        }
        return this;
    }

    /** The bound (context-aware) action, or null. */
    public Action getAction() {
        return binding == null ? null : binding.getAction();
    }

    @Override
    protected void attached() {
        if (binding != null) {
            binding.start();
        }
    }

    @Override
    protected void detached() {
        if (binding != null) {
            binding.stop();
        }
    }
}
