package org.webbeans.progress;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.webbeans.core.registry.Registration;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.UI;

/**
 * The progress cell of the status bar ({@code wb.ProgressCell}): the newest running handle's bar and message,
 * the count of running tasks, a cancel button when the task allows it, and a popup list of all running tasks.
 * Updates are coalesced to at most ten per second per tab.
 */
@Renderer(id = "wb.ProgressCell", resource = "org/webbeans/actions/client/progress.js", css = "org/webbeans/actions/client/actions.css")
public final class ProgressStatusCell extends Component implements ProgressRegistry.Listener {

    public static final Property<String> TEXT = Property.of("text", String.class, null);
    public static final Property<Double> VALUE = Property.of("value", Double.class, -1.0);
    public static final Property<Integer> COUNT = Property.of("count", Integer.class, 0);
    public static final Property<Boolean> CANCELLABLE = Property.of("cancellable", Boolean.class, Boolean.FALSE);
    public static final Property<List<Map<String, Object>>> TASKS = Property.of("tasks", listClass(), null);
    public static final EventType<Event> CANCEL = EventType.of("cancel", Event::new);

    private ProgressRegistry registry;
    private UI.Timer pending;

    public ProgressStatusCell() {
        super("wb.ProgressCell");
        setTestId("progress-cell");
        addListener(CANCEL, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                int index = e.getInt("index", -1);
                List<ProgressHandle> running = registry == null ? new ArrayList<ProgressHandle>() : registry.getRunning();
                ProgressHandle h = index >= 0 && index < running.size() ? running.get(index) : running.isEmpty() ? null : running.get(running.size() - 1);
                if (h != null) {
                    h.cancel();
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Class<List<Map<String, Object>>> listClass() {
        return (Class<List<Map<String, Object>>>) (Class<?>) List.class;
    }

    @Registration(path = "StatusBar", type = Component.class, position = 800)
    public static Component create() {
        return new ProgressStatusCell();
    }

    @Override
    protected void attached() {
        registry = ProgressRegistry.of(getUI().getSession());
        registry.addListener(this);
        push();
    }

    @Override
    protected void detached() {
        if (registry != null) {
            registry.removeListener(this);
            registry = null;
        }
        if (pending != null) {
            pending.cancel();
            pending = null;
        }
    }

    @Override
    public void progressChanged() {
        final UI ui = getUI();
        if (ui == null) {
            return;
        }
        synchronized (this) {
            if (pending != null) {
                return;
            }
            pending = ui.schedule(new Runnable() {
                @Override
                public void run() {
                    synchronized (ProgressStatusCell.this) {
                        pending = null;
                    }
                    if (isAttached()) {
                        push();
                    }
                }
            }, 100);
        }
    }

    private void push() {
        List<ProgressHandle> running = registry.getRunning();
        set(COUNT, running.size());
        if (running.isEmpty()) {
            set(TEXT, null);
            set(VALUE, -1.0);
            set(CANCELLABLE, Boolean.FALSE);
            set(TASKS, null);
            return;
        }
        ProgressHandle h = running.get(running.size() - 1);
        String text = h.getDisplayName();
        if (h.getMessage() != null && !h.getMessage().isEmpty()) {
            text += ": " + h.getMessage();
        }
        set(TEXT, text);
        set(VALUE, h.getFraction());
        set(CANCELLABLE, h.isCancellable() && !h.isCancelled());
        List<Map<String, Object>> tasks = new ArrayList<Map<String, Object>>();
        for (ProgressHandle r : running) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("name", r.getDisplayName());
            m.put("message", r.getMessage());
            m.put("value", r.getFraction());
            m.put("cancellable", r.isCancellable() && !r.isCancelled());
            tasks.add(m);
        }
        set(TASKS, tasks);
    }
}
