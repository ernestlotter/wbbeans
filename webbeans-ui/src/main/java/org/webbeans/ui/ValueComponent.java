package org.webbeans.ui;

import java.util.Map;

/**
 * A component with a client-owned {@code value}. The client sends {@code change} on commit; the server converts,
 * validates (setting {@link #ERROR}) and fires {@link ChangeEvent}. Server-side {@link #setValue} is sent to the
 * client; client-originated values are stored without an echo.
 */
public abstract class ValueComponent<T> extends Component {

    public static final Property<Boolean> READ_ONLY = Property.of("readOnly", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> REQUIRED = Property.of("required", Boolean.class, Boolean.FALSE);
    public static final Property<String> ERROR = Property.of("error", String.class, null);
    public static final String CHANGE = "change";

    private final Property<T> valueProperty;
    private Validator<T> validator;
    private boolean changeListened;

    protected ValueComponent(String rendererId, Property<T> valueProperty) {
        super(rendererId);
        this.valueProperty = valueProperty;
        // the component always listens for change so the value stays in sync
        addListener(changeType(), new Listener<ChangeEvent<T>>() {
            @Override
            public void onEvent(ChangeEvent<T> e) {
            }
        });
    }

    /** The {@code change} event type; the event carries the converted value. */
    public final EventType<ChangeEvent<T>> changeType() {
        return EventType.of(CHANGE, new java.util.function.BiFunction<Component, Map<String, Object>, ChangeEvent<T>>() {
            @Override
            public ChangeEvent<T> apply(Component c, Map<String, Object> data) {
                return change(data);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private ChangeEvent<T> change(Map<String, Object> data) {
        T old = get(valueProperty);
        T value;
        try {
            value = fromClient(data.get("value"));
        } catch (RuntimeException e) {
            set(ERROR, e.getMessage() != null ? e.getMessage() : "Invalid value");
            resend(valueProperty); // the client shows the rejected text: send it the last valid value again
            return new ChangeEvent<T>(this, data, old, old, false);
        }
        setFromClient(valueProperty, value);
        String err = validateValue(value);
        set(ERROR, err);
        return new ChangeEvent<T>(this, data, value, old, err == null);
    }

    /** Converts the raw JSON value (String, Number, Boolean, List, Map) to {@code T}; throw to reject. */
    protected abstract T fromClient(Object raw);

    /**
     * The complete validation of a value: {@link #REQUIRED} and emptiness, the subclass's own rules
     * ({@link #validateRange}), then the application's {@link Validator}. Returns the error text or null.
     */
    protected String validateValue(T value) {
        if (get(REQUIRED) && isEmpty(value)) {
            return requiredMessage();
        }
        String err = validateRange(value);
        if (err == null && validator != null) {
            err = validator.validate(value);
        }
        return err;
    }

    /** Subclass rule hook (a number outside min/max, a date outside the allowed range); null when fine. */
    protected String validateRange(T value) {
        return null;
    }

    /** Whether the value counts as "nothing entered" for {@link #REQUIRED}. Default: null or an empty string. */
    protected boolean isEmpty(T value) {
        return value == null || (value instanceof CharSequence && ((CharSequence) value).length() == 0);
    }

    /** The error shown for an empty required field (localised, {@code ERR_Required}). */
    protected String requiredMessage() {
        return org.webbeans.core.i18n.Bundles.getMessage(ValueComponent.class, "ERR_Required");
    }

    /**
     * Validates the current value now (as a {@code change} would) and updates {@link #ERROR}; true when valid.
     * {@link Form} calls this before a submit so untouched fields are checked too.
     */
    public boolean validate() {
        String err = validateValue(getValue());
        set(ERROR, err);
        return err == null;
    }

    /** True when the last validation (change or {@link #validate}) found no error. */
    public boolean isValid() {
        return get(ERROR) == null;
    }

    public T getValue() {
        return get(valueProperty);
    }

    public ValueComponent<T> setValue(T value) {
        set(valueProperty, value);
        return this;
    }

    public ValueComponent<T> setValidator(Validator<T> v) {
        this.validator = v;
        return this;
    }

    public Registration addChangeListener(Listener<ChangeEvent<T>> l) {
        return addListener(changeType(), l);
    }

    public ValueComponent<T> setReadOnly(boolean b) {
        set(READ_ONLY, b);
        return this;
    }

    public ValueComponent<T> setRequired(boolean b) {
        set(REQUIRED, b);
        return this;
    }

    public String getError() {
        return get(ERROR);
    }
}
