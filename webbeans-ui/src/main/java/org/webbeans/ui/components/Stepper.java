package org.webbeans.ui.components;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;

/**
 * Shows progress through numbered steps (a wizard's header, an order's status): done, current and pending
 * steps, an optional error step. Clicking a completed step fires {@link #SELECT} when {@link #setClickable}.
 */
@Renderer(id = "wb.Stepper", resource = "org/webbeans/ui/client/renderers/widgets.js", css = "org/webbeans/ui/client/renderers/widgets.css")
public class Stepper extends Component {

    public enum Orientation { HORIZONTAL, VERTICAL }

    /** A step was clicked: {@link #getIndex()}. */
    public static class SelectEvent extends Event {
        public SelectEvent(Component source, Map<String, Object> data) {
            super(source, data);
        }

        public int getIndex() {
            return getInt("index", -1);
        }
    }

    @SuppressWarnings("unchecked")
    public static final Property<List<Map<String, Object>>> STEPS = (Property<List<Map<String, Object>>>) (Property<?>) Property.of("steps", List.class, new ArrayList<Object>());
    public static final Property<Integer> CURRENT = Property.of("current", Integer.class, Integer.valueOf(0));
    public static final Property<Integer> ERROR_STEP = Property.of("errorStep", Integer.class, null);
    public static final Property<Orientation> ORIENTATION = Property.of("orientation", Orientation.class, Orientation.HORIZONTAL);
    public static final Property<Boolean> CLICKABLE = Property.of("clickable", Boolean.class, Boolean.FALSE);
    /** Every step is clickable, not only completed ones. */
    public static final Property<Boolean> FREE = Property.of("free", Boolean.class, Boolean.FALSE);

    public static final EventType<SelectEvent> SELECT = EventType.of("select", SelectEvent::new);

    private final List<Map<String, Object>> steps = new ArrayList<Map<String, Object>>();

    public Stepper() {
        super("wb.Stepper");
    }

    public Stepper(String... titles) {
        this();
        for (String t : titles) {
            addStep(t);
        }
    }

    public Stepper addStep(String title) {
        return addStep(title, null);
    }

    public Stepper addStep(String title, String description) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("title", title);
        if (description != null) {
            m.put("desc", description);
        }
        steps.add(m);
        set(STEPS, new ArrayList<Map<String, Object>>(steps));
        return this;
    }

    public Stepper clearSteps() {
        steps.clear();
        set(STEPS, new ArrayList<Map<String, Object>>());
        return this;
    }

    public int getStepCount() {
        return steps.size();
    }

    public Stepper setCurrent(int index) {
        set(CURRENT, Math.max(0, Math.min(steps.size(), index)));
        return this;
    }

    public int getCurrent() {
        return get(CURRENT);
    }

    public Stepper next() {
        return setCurrent(getCurrent() + 1);
    }

    public Stepper previous() {
        return setCurrent(getCurrent() - 1);
    }

    /** {@code getCurrent() == getStepCount()}: all steps done. */
    public boolean isFinished() {
        return getCurrent() >= steps.size();
    }

    public Stepper setErrorStep(Integer index) {
        set(ERROR_STEP, index);
        return this;
    }

    public Stepper setOrientation(Orientation o) {
        set(ORIENTATION, o);
        return this;
    }

    public Stepper setClickable(boolean b) {
        set(CLICKABLE, b);
        return this;
    }

    public Stepper setFree(boolean b) {
        set(FREE, b);
        return this;
    }

    public Registration addSelectListener(Listener<SelectEvent> l) {
        return addListener(SELECT, l);
    }
}
