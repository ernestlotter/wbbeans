package org.webbeans.workbench.files;

import java.io.IOException;

import org.openide.util.Lookup;
import org.webbeans.actions.ActionMap;
import org.webbeans.core.i18n.Messages;
import org.webbeans.dialogs.DialogDisplayer;
import org.webbeans.dialogs.NotifyDescriptor;
import org.webbeans.explorer.ExplorerManager;
import org.webbeans.explorer.ExplorerUtils;
import org.webbeans.explorer.OutlineView;
import org.webbeans.nodes.Node;
import org.webbeans.ui.components.Layout;
import org.webbeans.windows.TopComponent;

/**
 * The Files window: an {@link OutlineView} over the user's working directory with size and modification columns.
 * The selection and an {@link ActionMap} (copy/cut/paste/delete wired by {@link ExplorerUtils}) go into the
 * window's lookup, so the Properties window, the Edit menu and the registered file actions follow it.
 */
@TopComponent.Registration(mode = "explorer", openAtStartup = true, position = 100)
@TopComponent.Description(preferredID = "FilesTC", iconBase = "org/webbeans/workbench/files/icons/files.svg", helpId = "files")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenFiles", position = 100)
@Messages({"CTL_FilesModule=Workbench Files", "CTL_FilesTC=Files", "CTL_OpenFiles=Files", "COL_Name=Name", "COL_Size=Size", "COL_Modified=Modified"})
public final class FilesTopComponent extends TopComponent implements ExplorerManager.Provider {

    private final ExplorerManager em = new ExplorerManager();
    private final ActionMap map = new ActionMap();
    private final OutlineView view = new OutlineView(Bundle.COL_Name());

    public FilesTopComponent() {
        setDisplayName(Bundle.CTL_FilesTC());
        setLayout(new Layout.Border());
        ExplorerUtils.activateActions(em, map, true);
        associateLookup(ExplorerUtils.createLookup(em, map));
        view.setPropertyColumns("size", Bundle.COL_Size(), "modified", Bundle.COL_Modified());
        view.setColumnWidth("size", 70).setColumnWidth("modified", 130);
        view.setRootVisible(true);
        view.setTestId("files");
        add(view, Layout.Border.CENTER);
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return em;
    }

    @Override
    protected void localeChanged() {
        setDisplayName(Bundle.CTL_FilesTC());
    }

    @Override
    protected void componentOpened() {
        if (em.getRootContext() == null) {
            try {
                FileService fs = Lookup.getDefault().lookup(FileService.class);
                em.setRootContext(new FileNode(fs.getWorkDir()));
            } catch (IOException e) {
                DialogDisplayer.getDefault().notify(new NotifyDescriptor.Exception(e));
            }
        }
    }

    /** The selected directory, or the directory of the selected file, or the root. */
    static java.nio.file.Path targetDirectory(ExplorerManager em) {
        Node[] sel = em.getSelectedNodes();
        if (sel.length > 0) {
            java.nio.file.Path p = sel[0].getLookup().lookup(java.nio.file.Path.class);
            if (p != null) {
                return java.nio.file.Files.isDirectory(p) ? p : p.getParent();
            }
        }
        Node root = em.getRootContext();
        return root == null ? null : root.getLookup().lookup(java.nio.file.Path.class);
    }
}
