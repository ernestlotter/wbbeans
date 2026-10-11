package org.webbeans.tutorial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.webbeans.core.registry.Registration;
import org.webbeans.ui.UISession;

/**
 * Tutorial §6/§12: the user's tasks — one instance per session (registered under {@code Services/Session}),
 * reachable through {@code UISession.current().getLookup()}. Listeners are called on the session thread.
 */
@Registration(path = "Services/Session", type = TaskList.class)
public final class TaskList {

    public interface Listener {
        void tasksChanged();
    }

    private final List<Task> tasks = new ArrayList<Task>();
    private final List<Listener> listeners = new ArrayList<Listener>();

    public TaskList() {
        tasks.add(new Task("Read the user's guide"));
        tasks.add(new Task("Build the Workbench"));
    }

    public static TaskList get() {
        return UISession.current().getLookup().lookup(TaskList.class);
    }

    public List<Task> getTasks() {
        return Collections.unmodifiableList(new ArrayList<Task>(tasks));
    }

    public Task add(String title) {
        Task t = new Task(title);
        tasks.add(t);
        fire();
        return t;
    }

    public void remove(Task t) {
        tasks.remove(t);
        fire();
    }

    public void setDone(Task t, boolean done) {
        t.setDone(done);
        fire();
    }

    public void rename(Task t, String title) {
        t.setTitle(title);
        fire();
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void fire() {
        for (Listener l : new ArrayList<Listener>(listeners)) {
            l.tasksChanged();
        }
    }
}
