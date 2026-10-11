package org.webbeans.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Groups {@link ValueComponent}s and tracks their combined validity — not a component, a helper. Add the fields,
 * then either bind a button ({@link #enable}) so it follows the form's validity, or ask {@link #validate()} before
 * a submit (which also checks fields the user never touched) and {@link #write()} the values into the model
 * through the consumers given to {@link #bind}.
 *
 * <pre>
 * Form form = new Form().add(name, email, age);
 * form.bind(name, contact::setName).bind(age, v -&gt; contact.setAge(v.intValue()));
 * form.enable(save);                          // save.setEnabled(form.isValid()) after every change
 * save.addClickListener(e -&gt; { if (form.validate()) { form.write(); ... } });
 * </pre>
 */
public final class Form {

    /** Fired after any field of the form changed (after that field's validation). */
    public interface ChangeHandler {
        void changed(Form form, ValueComponent<?> field);
    }

    private final List<ValueComponent<?>> fields = new ArrayList<ValueComponent<?>>();
    private final Map<ValueComponent<?>, Consumer<Object>> writers = new LinkedHashMap<ValueComponent<?>, Consumer<Object>>();
    private final List<ChangeHandler> handlers = new ArrayList<ChangeHandler>();
    private final List<Component> enabled = new ArrayList<Component>();
    private final Map<ValueComponent<?>, Boolean> touched = new LinkedHashMap<ValueComponent<?>, Boolean>();

    public Form add(ValueComponent<?>... components) {
        for (final ValueComponent<?> c : components) {
            if (fields.contains(c)) {
                continue;
            }
            fields.add(c);
            touched.put(c, Boolean.FALSE);
            addChangeHook(c);
        }
        refresh();
        return this;
    }

    private <T> void addChangeHook(final ValueComponent<T> c) {
        c.addChangeListener(new Listener<ChangeEvent<T>>() {
            @Override
            public void onEvent(ChangeEvent<T> e) {
                touched.put(c, Boolean.TRUE);
                refresh();
                for (ChangeHandler h : new ArrayList<ChangeHandler>(handlers)) {
                    h.changed(Form.this, c);
                }
            }
        });
    }

    /** Registers where a field's value goes on {@link #write()}. */
    @SuppressWarnings("unchecked")
    public <T> Form bind(ValueComponent<T> field, Consumer<? super T> writer) {
        if (!fields.contains(field)) {
            add(field);
        }
        writers.put(field, (Consumer<Object>) writer);
        return this;
    }

    /** Keeps the component's {@code enabled} in step with the form's validity (a Save button). */
    public Form enable(Component c) {
        enabled.add(c);
        c.setEnabled(isValid());
        return this;
    }

    public Form addChangeHandler(ChangeHandler h) {
        handlers.add(h);
        return this;
    }

    public List<ValueComponent<?>> getFields() {
        return new ArrayList<ValueComponent<?>>(fields);
    }

    /**
     * Whether every field is currently valid. Fields the user has not touched count as valid unless they are
     * required and empty, so a Save button starts disabled on an empty required form without showing errors.
     */
    public boolean isValid() {
        for (ValueComponent<?> f : fields) {
            if (!f.isValid()) {
                return false;
            }
            if (!touched.get(f) && f.get(ValueComponent.REQUIRED) && isEmpty(f)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static <T> boolean isEmpty(ValueComponent<T> f) {
        return f.isEmpty(f.getValue());
    }

    /** Validates every field now, showing errors on the untouched ones too; true when all are valid. */
    public boolean validate() {
        boolean ok = true;
        for (ValueComponent<?> f : fields) {
            touched.put(f, Boolean.TRUE);
            if (!f.validate()) {
                ok = false;
            }
        }
        refresh();
        return ok;
    }

    /** Pushes every bound field's value into its consumer. */
    @SuppressWarnings("unchecked")
    public void write() {
        for (Map.Entry<ValueComponent<?>, Consumer<Object>> e : writers.entrySet()) {
            e.getValue().accept(((ValueComponent<Object>) e.getKey()).getValue());
        }
    }

    /** Clears every error and forgets which fields were touched (after a successful save, or a reset). */
    public void reset() {
        for (ValueComponent<?> f : fields) {
            f.set(ValueComponent.ERROR, null);
            touched.put(f, Boolean.FALSE);
        }
        refresh();
    }

    public Form setReadOnly(boolean b) {
        for (ValueComponent<?> f : fields) {
            f.setReadOnly(b);
        }
        return this;
    }

    /** The first field with an error, or null. */
    public ValueComponent<?> firstInvalid() {
        for (ValueComponent<?> f : fields) {
            if (!f.isValid()) {
                return f;
            }
        }
        return null;
    }

    private void refresh() {
        boolean valid = isValid();
        for (Component c : enabled) {
            c.setEnabled(valid);
        }
    }
}
