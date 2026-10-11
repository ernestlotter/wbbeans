package org.webbeans.nodes;

import java.io.IOException;
import java.util.Arrays;

import org.openide.util.Lookup;
import org.webbeans.actions.Action;
import org.webbeans.ui.Resource;

/** A concrete node with setters for everything: display, icon base, actions, property sheet. */
public class AbstractNode extends Node {

    private Action[] actions = new Action[0];
    private Action preferredAction;
    private Sheet sheet;
    private boolean canRename;
    private boolean canDestroy;
    private boolean canCut;

    public AbstractNode(Children children) {
        super(children);
    }

    public AbstractNode(Children children, Lookup lookup) {
        super(children, lookup);
    }

    /** Sets the icon from a resource path such as {@code org/acme/icons/file.svg}. */
    public final void setIconBase(String path) {
        setIcon(path == null ? null : Resource.of(path));
    }

    /** NetBeans name for {@link #setIconBase}. */
    public final void setIconBaseWithExtension(String path) {
        setIconBase(path);
    }

    public final void setActions(Action... actions) {
        this.actions = actions == null ? new Action[0] : actions.clone();
    }

    @Override
    public Action[] getActions(boolean context) {
        return actions.clone();
    }

    public final void setPreferredAction(Action a) {
        this.preferredAction = a;
    }

    @Override
    public Action getPreferredAction() {
        return preferredAction != null ? preferredAction : super.getPreferredAction();
    }

    /** The property sheet; created on first access (override {@link #createSheet}). */
    public final Sheet getSheet() {
        if (sheet == null) {
            sheet = createSheet();
        }
        return sheet;
    }

    public final void setSheet(Sheet s) {
        PropertySet[] old = getPropertySets();
        sheet = s;
        firePropertySetsChange(old, getPropertySets());
    }

    protected Sheet createSheet() {
        return new Sheet();
    }

    @Override
    public PropertySet[] getPropertySets() {
        return getSheet().toArray();
    }

    public final void setCanRename(boolean b) {
        canRename = b;
    }

    public final void setCanDestroy(boolean b) {
        canDestroy = b;
    }

    public final void setCanCut(boolean b) {
        canCut = b;
    }

    @Override
    public boolean canRename() {
        return canRename;
    }

    @Override
    public boolean canDestroy() {
        return canDestroy;
    }

    @Override
    public boolean canCut() {
        return canCut;
    }

    @Override
    public void destroy() throws IOException {
        if (!canDestroy()) {
            throw new IOException("Cannot destroy " + getDisplayName());
        }
        super.destroy();
    }

    @Override
    public String toString() {
        return "AbstractNode[" + getDisplayName() + ", actions=" + Arrays.toString(actions) + "]";
    }
}
