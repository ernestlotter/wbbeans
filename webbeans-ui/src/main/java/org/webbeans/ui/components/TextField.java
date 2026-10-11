package org.webbeans.ui.components;

import java.util.Map;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;
import org.webbeans.ui.Resource;
import org.webbeans.ui.ValueComponent;

/** Single-line text input. {@code change} fires on commit (blur/Enter); {@link #INPUT} while typing, debounced. */
@Renderer(id = "wb.TextField", resource = "org/webbeans/ui/client/renderers/textfield.js")
public class TextField extends ValueComponent<String> {

    public static final Property<String> VALUE = Property.of("value", String.class, "").clientOwned();
    public static final Property<String> PLACEHOLDER = Property.of("placeholder", String.class, null);
    public static final Property<Integer> MAX_LENGTH = Property.of("maxLength", Integer.class, null);
    public static final Property<Boolean> PASSWORD = Property.of("password", Boolean.class, Boolean.FALSE);
    public static final Property<Resource> PREFIX_ICON = Property.of("prefixIcon", Resource.class, null);
    public static final Property<Boolean> CLEAR_BUTTON = Property.of("clearButton", Boolean.class, Boolean.FALSE);

    /** While typing (debounced 300 ms); {@code getString("value")} is the current text. */
    public static final EventType<InputEvent> INPUT = EventType.of("input", InputEvent::new).lazy(300);
    public static final EventType<Event> KEY = EventType.of("key", Event::new);

    public static class InputEvent extends Event {
        public InputEvent(org.webbeans.ui.Component source, Map<String, Object> data) {
            super(source, data);
        }

        public String getValue() {
            return getString("value");
        }
    }

    public TextField() {
        this("wb.TextField", VALUE);
    }

    public TextField(String value) {
        this();
        setValue(value);
    }

    protected TextField(String rendererId, Property<String> valueProperty) {
        super(rendererId, valueProperty);
    }

    @Override
    protected String fromClient(Object raw) {
        return raw == null ? "" : raw.toString();
    }

    @Override
    protected void onEvent(Event event) {
        if (event instanceof InputEvent) {
            setFromClient(VALUE, ((InputEvent) event).getValue());
        }
    }

    public TextField setPlaceholder(String p) {
        set(PLACEHOLDER, p);
        return this;
    }

    public TextField setMaxLength(Integer n) {
        set(MAX_LENGTH, n);
        return this;
    }

    public TextField setPassword(boolean b) {
        set(PASSWORD, b);
        return this;
    }

    public TextField setPrefixIcon(Resource r) {
        set(PREFIX_ICON, r);
        return this;
    }

    public TextField setClearButton(boolean b) {
        set(CLEAR_BUTTON, b);
        return this;
    }

    public Registration addInputListener(Listener<InputEvent> l) {
        return addListener(INPUT, l);
    }

    public void focus() {
        call("focus");
    }

    public void selectAll() {
        call("select");
    }
}
