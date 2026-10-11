package org.webbeans.actions;

/**
 * The global edit actions: callback actions that delegate to the activated window's {@link ActionMap}
 * ({@code copy}, {@code cut}, {@code paste}, {@code delete}, {@code rename}, {@code find}). Abstract classes:
 * a callback registration on an abstract class has no fallback, so the item is disabled when the active
 * window does not provide the key. Explorer windows get them from
 * {@code ExplorerUtils.createLookup(em, map)} after {@code ExplorerUtils.activateActions(em, map)}.
 */
public final class SystemActions {

    private SystemActions() {
    }

    @ActionID(category = "Edit", id = "org.webbeans.actions.CopyAction")
    @ActionRegistration(displayName = "#CTL_Copy", key = ActionMap.COPY, iconBase = "org/webbeans/actions/client/copy.svg")
    @ActionReferences({
        @ActionReference(path = "Menu/Edit", position = 100),
        @ActionReference(path = "Toolbars/Edit", position = 100),
        @ActionReference(path = "Shortcuts", name = "D-C")})
    public abstract static class CopyAction implements ActionListener {
    }

    @ActionID(category = "Edit", id = "org.webbeans.actions.CutAction")
    @ActionRegistration(displayName = "#CTL_Cut", key = ActionMap.CUT, iconBase = "org/webbeans/actions/client/cut.svg")
    @ActionReferences({
        @ActionReference(path = "Menu/Edit", position = 200),
        @ActionReference(path = "Toolbars/Edit", position = 200),
        @ActionReference(path = "Shortcuts", name = "D-X")})
    public abstract static class CutAction implements ActionListener {
    }

    @ActionID(category = "Edit", id = "org.webbeans.actions.PasteAction")
    @ActionRegistration(displayName = "#CTL_Paste", key = ActionMap.PASTE, iconBase = "org/webbeans/actions/client/paste.svg")
    @ActionReferences({
        @ActionReference(path = "Menu/Edit", position = 300, separatorAfter = 350),
        @ActionReference(path = "Toolbars/Edit", position = 300),
        @ActionReference(path = "Shortcuts", name = "D-V")})
    public abstract static class PasteAction implements ActionListener {
    }

    @ActionID(category = "Edit", id = "org.webbeans.actions.DeleteAction")
    @ActionRegistration(displayName = "#CTL_Delete", key = ActionMap.DELETE, iconBase = "org/webbeans/actions/client/delete.svg")
    @ActionReferences({
        @ActionReference(path = "Menu/Edit", position = 400),
        @ActionReference(path = "Shortcuts", name = "DELETE")})
    public abstract static class DeleteAction implements ActionListener {
    }

    @ActionID(category = "Edit", id = "org.webbeans.actions.RenameAction")
    @ActionRegistration(displayName = "#CTL_Rename", key = ActionMap.RENAME)
    @ActionReferences({
        @ActionReference(path = "Menu/Edit", position = 500),
        @ActionReference(path = "Shortcuts", name = "F2")})
    public abstract static class RenameAction implements ActionListener {
    }

    @ActionID(category = "Edit", id = "org.webbeans.actions.FindAction")
    @ActionRegistration(displayName = "#CTL_Find", key = "find")
    @ActionReferences({
        @ActionReference(path = "Menu/Edit", position = 600, separatorBefore = 550),
        @ActionReference(path = "Shortcuts", name = "D-F")})
    public abstract static class FindAction implements ActionListener {
    }
}
