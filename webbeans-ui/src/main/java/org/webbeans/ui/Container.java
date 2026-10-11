package org.webbeans.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A component with children. Adding an attached component elsewhere moves it. */
public abstract class Container extends Component {

    private final List<Component> children = new ArrayList<Component>();

    protected Container(String rendererId) {
        super(rendererId);
    }

    public List<Component> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public int getChildCount() {
        return children.size();
    }

    public Component add(Component child) {
        return add(child, null, -1);
    }

    public Component add(Component child, Object constraint) {
        return add(child, constraint, -1);
    }

    /** @param index position among the children, or -1 to append */
    public Component add(Component child, Object constraint, int index) {
        checkThread();
        if (child == null) {
            throw new NullPointerException("child");
        }
        for (Component c = this; c != null; c = c.getParent()) {
            if (c == child) {
                throw new IllegalArgumentException("Cannot add a component to itself or a descendant");
            }
        }
        if (child.getParent() != null) {
            ((Container) child.getParent()).remove(child);
        }
        if (index < 0 || index > children.size()) {
            index = children.size();
        }
        children.add(index, child);
        child.setParent(this, constraint);
        UI ui = getUI();
        if (ui != null) {
            ui.attach(child, this, index);
        }
        return child;
    }

    public void remove(Component child) {
        checkThread();
        int i = children.indexOf(child);
        if (i < 0) {
            return;
        }
        UI ui = getUI();
        if (ui != null) {
            ui.detach(child);
        }
        children.remove(i);
        child.setParent(null, null);
    }

    public void removeAll() {
        for (Component c : new ArrayList<Component>(children)) {
            remove(c);
        }
    }

    public int indexOf(Component child) {
        return children.indexOf(child);
    }

    /** Depth-first iteration over the whole subtree, this container first. */
    void walk(List<Component> into) {
        into.add(this);
        for (Component c : children) {
            if (c instanceof Container) {
                ((Container) c).walk(into);
            } else {
                into.add(c);
            }
        }
    }
}
