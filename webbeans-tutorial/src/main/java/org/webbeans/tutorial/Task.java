package org.webbeans.tutorial;

/** A to-do item (plain data; the node and the views are built over it). */
public final class Task {

    private String title;
    private boolean done;

    public Task(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    void setTitle(String t) {
        title = t;
    }

    public boolean isDone() {
        return done;
    }

    void setDone(boolean d) {
        done = d;
    }
}
