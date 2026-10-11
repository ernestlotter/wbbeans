package org.webbeans.dialogs;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.webbeans.ui.Component;

/**
 * A multi-step dialog (minimal NetBeans {@code WizardDescriptor}): an {@link Iterator} over {@link Panel}s,
 * Back/Next/Finish/Cancel buttons driven by {@link Panel#isValid()}, a step list on the left, and a property
 * map the panels share through {@link Panel#readSettings}/{@link Panel#storeSettings}. The future of
 * {@link DialogDisplayer#notify} completes with {@link #FINISH_OPTION} or {@link #CANCEL_OPTION}.
 *
 * @param <D> the settings object passed to the panels (the descriptor itself by default)
 */
public final class WizardDescriptor<D> extends DialogDescriptor {

    public static final Object FINISH_OPTION = option("Finish");
    public static final Object NEXT_OPTION = option("Next");
    public static final Object PREVIOUS_OPTION = option("Back");
    public static final String PROP_ERROR_MESSAGE = "WizardPanel_errorMessage";

    /** One step. */
    public interface Panel<D> {
        /** The step's content; created once and reused. */
        Component getComponent();

        /** The step name shown in the step list. */
        String getName();

        boolean isValid();

        void readSettings(D settings);

        void storeSettings(D settings);

        void addChangeListener(ChangeListener l);

        void removeChangeListener(ChangeListener l);
    }

    /** Which panel comes next. */
    public interface Iterator<D> {
        Panel<D> current();

        boolean hasNext();

        boolean hasPrevious();

        void nextPanel();

        void previousPanel();

        /** All step names, for the list on the left (null = unknown). */
        String[] getStepNames();
    }

    /** Runs code when the user finishes (create files, objects...). */
    public interface InstantiatingIterator<D> extends Iterator<D> {
        Set<?> instantiate() throws IOException;

        void initialize(WizardDescriptor<D> wizard);

        void uninitialize(WizardDescriptor<D> wizard);
    }

    /** A fixed sequence of panels. */
    public static class ArrayIterator<D> implements Iterator<D> {
        private final Panel<D>[] panels;
        private int index;

        @SafeVarargs
        @SuppressWarnings("varargs")
        public ArrayIterator(Panel<D>... panels) {
            if (panels.length == 0) {
                throw new IllegalArgumentException("No panels");
            }
            this.panels = panels.clone();
        }

        @Override
        public Panel<D> current() {
            return panels[index];
        }

        @Override
        public boolean hasNext() {
            return index < panels.length - 1;
        }

        @Override
        public boolean hasPrevious() {
            return index > 0;
        }

        @Override
        public void nextPanel() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            index++;
        }

        @Override
        public void previousPanel() {
            if (!hasPrevious()) {
                throw new java.util.NoSuchElementException();
            }
            index--;
        }

        @Override
        public String[] getStepNames() {
            String[] names = new String[panels.length];
            for (int i = 0; i < panels.length; i++) {
                names[i] = panels[i].getName();
            }
            return names;
        }

        public int getIndex() {
            return index;
        }
    }

    /** A convenient panel base: change support and default settings methods. */
    public abstract static class AbstractPanel<D> implements Panel<D> {
        private final javax.swing.event.EventListenerList listeners = new javax.swing.event.EventListenerList();
        private final String name;

        protected AbstractPanel(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public void readSettings(D settings) {
        }

        @Override
        public void storeSettings(D settings) {
        }

        @Override
        public void addChangeListener(ChangeListener l) {
            listeners.add(ChangeListener.class, l);
        }

        @Override
        public void removeChangeListener(ChangeListener l) {
            listeners.remove(ChangeListener.class, l);
        }

        protected final void fireChange() {
            ChangeEvent e = new ChangeEvent(this);
            for (ChangeListener l : listeners.getListeners(ChangeListener.class)) {
                l.stateChanged(e);
            }
        }
    }

    private final Iterator<D> iterator;
    private final D data;
    private final Map<String, Object> properties = new LinkedHashMap<String, Object>();
    private Set<?> instantiated = Collections.emptySet();

    @SuppressWarnings("unchecked")
    public WizardDescriptor(Iterator<D> iterator) {
        this(iterator, null);
    }

    @SuppressWarnings("unchecked")
    public WizardDescriptor(Iterator<D> iterator, D data) {
        super(null, "", true, new Object[] {PREVIOUS_OPTION, NEXT_OPTION, FINISH_OPTION, CANCEL_OPTION}, NEXT_OPTION, RIGHT_ALIGN, null);
        this.iterator = iterator;
        this.data = data != null ? data : (D) this;
        if (iterator instanceof InstantiatingIterator) {
            ((InstantiatingIterator<D>) iterator).initialize(this);
        }
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <D> WizardDescriptor<D> of(Panel<D>... panels) {
        return new WizardDescriptor<D>(new ArrayIterator<D>(panels));
    }

    public Iterator<D> getIterator() {
        return iterator;
    }

    /** The settings object the panels read and store. */
    public D getData() {
        return data;
    }

    public Object getProperty(String name) {
        return properties.get(name);
    }

    public void putProperty(String name, Object value) {
        properties.put(name, value);
        if (PROP_ERROR_MESSAGE.equals(name)) {
            fire(PROP_ERROR_NOTIFICATION, null, value);
        }
    }

    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    /** What an {@link InstantiatingIterator} created on finish (empty otherwise). */
    public Set<?> getInstantiatedObjects() {
        return instantiated;
    }

    void setInstantiatedObjects(Set<?> objects) {
        instantiated = objects == null ? Collections.emptySet() : objects;
    }
}
