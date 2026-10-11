package org.webbeans.ui.components;

import java.util.Map;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;
import org.webbeans.ui.ValueComponent;

/**
 * A range slider. {@code change} fires when the knob is released (or on keyboard steps); {@link #INPUT} follows
 * the drag, throttled, and keeps the server value current. {@link #TICKS} shows a tick per step (or every
 * {@code ticks} units), {@link #SHOW_VALUE} a live value label.
 */
@Renderer(id = "wb.Slider", resource = "org/webbeans/ui/client/renderers/slider.js")
public class Slider extends ValueComponent<Double> {

    public enum Orientation { HORIZONTAL, VERTICAL }

    public static final Property<Double> VALUE = Property.of("value", Double.class, Double.valueOf(0)).clientOwned();
    public static final Property<Double> MIN = Property.of("min", Double.class, Double.valueOf(0));
    public static final Property<Double> MAX = Property.of("max", Double.class, Double.valueOf(100));
    public static final Property<Double> STEP = Property.of("step", Double.class, Double.valueOf(1));
    public static final Property<Double> TICKS = Property.of("ticks", Double.class, null);
    public static final Property<Boolean> SHOW_VALUE = Property.of("showValue", Boolean.class, Boolean.FALSE);
    public static final Property<Orientation> ORIENTATION = Property.of("orientation", Orientation.class, Orientation.HORIZONTAL);

    /** While dragging (throttled 50 ms); {@link InputEvent#getValue()} is the current position. */
    public static final EventType<InputEvent> INPUT = EventType.of("input", InputEvent::new).lazy(50);

    public static class InputEvent extends Event {
        public InputEvent(org.webbeans.ui.Component source, Map<String, Object> data) {
            super(source, data);
        }

        public double getValue() {
            return getDouble("value", 0);
        }
    }

    public Slider() {
        super("wb.Slider", VALUE);
    }

    public Slider(double min, double max, double value) {
        this();
        setRange(min, max);
        setValue(value);
    }

    @Override
    protected Double fromClient(Object raw) {
        if (raw instanceof Number) {
            return clamp(((Number) raw).doubleValue());
        }
        if (raw == null || "".equals(raw)) {
            return get(MIN);
        }
        return clamp(Double.parseDouble(raw.toString()));
    }

    private double clamp(double v) {
        return Math.max(get(MIN), Math.min(get(MAX), v));
    }

    @Override
    protected void onEvent(Event event) {
        if (event instanceof InputEvent) {
            setFromClient(VALUE, clamp(((InputEvent) event).getValue()));
        }
    }

    public Slider setRange(double min, double max) {
        set(MIN, min);
        set(MAX, max);
        return this;
    }

    public Slider setStep(double step) {
        set(STEP, step);
        return this;
    }

    /** Tick marks every {@code every} units (null: none). */
    public Slider setTicks(Double every) {
        set(TICKS, every);
        return this;
    }

    public Slider setShowValue(boolean b) {
        set(SHOW_VALUE, b);
        return this;
    }

    public Slider setOrientation(Orientation o) {
        set(ORIENTATION, o);
        return this;
    }

    public double getDoubleValue() {
        Double v = getValue();
        return v == null ? get(MIN) : v;
    }

    public Registration addInputListener(Listener<InputEvent> l) {
        return addListener(INPUT, l);
    }
}
