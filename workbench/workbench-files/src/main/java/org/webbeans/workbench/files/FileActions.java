package org.webbeans.workbench.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.openide.util.Lookup;
import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionReferences;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.core.i18n.Messages;
import org.webbeans.dialogs.DialogDisplayer;
import org.webbeans.dialogs.NotifyDescriptor;
import org.webbeans.explorer.ExplorerManager;
import org.webbeans.nodes.Openable;
import org.webbeans.nodes.Savable;
import org.webbeans.windows.StatusDisplayer;
import org.webbeans.windows.TopComponent;
import org.webbeans.windows.WindowManager;
import org.webbeans.workbench.core.api.Log;

/**
 * The file actions, one registered class each: Save (context over {@link Savable}: enabled while an editor with
 * changes is active, Ctrl+S), New File / New Folder (always enabled; an input dialog, continued asynchronously),
 * Open (context over {@link Openable}, in the node popup) and Refresh (F5).
 */
@Messages({"CTL_Save=Save", "CTL_NewFile=New File…", "CTL_NewFolder=New Folder…", "CTL_Open=Open", "CTL_Refresh=Refresh",
    "LBL_FileName=File name", "LBL_FolderName=Folder name", "TITLE_NewFile=New File", "TITLE_NewFolder=New Folder",
    "MSG_Created=Created {0}", "MSG_Refreshed=Files refreshed", "ERR_BadName=Invalid name", "CTL_FileToolbar=File"})
@org.webbeans.core.registry.FolderRegistration(path = "Toolbars/File", displayName = "#CTL_FileToolbar", position = 100)
public final class FileActions {

    private FileActions() {
    }

    @ActionID(category = "File", id = "org.webbeans.workbench.files.SaveAction")
    @ActionRegistration(displayName = "#CTL_Save", iconBase = "org/webbeans/workbench/files/icons/save.svg", surviveFocusChange = true)
    @ActionReferences({
        @ActionReference(path = "Menu/File", position = 300),
        @ActionReference(path = "Toolbars/File", position = 300),
        @ActionReference(path = "Shortcuts", name = "D-S")})
    public static final class SaveAction implements ActionListener {
        private final Savable savable;

        public SaveAction(Savable savable) {
            this.savable = savable;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                savable.save();
            } catch (IOException ex) {
                DialogDisplayer.getDefault().notify(new NotifyDescriptor.Exception(ex));
            }
        }
    }

    @ActionID(category = "File", id = "org.webbeans.workbench.files.NewFileAction")
    @ActionRegistration(displayName = "#CTL_NewFile", iconBase = "org/webbeans/workbench/files/icons/new.svg")
    @ActionReferences({
        @ActionReference(path = "Menu/File", position = 100),
        @ActionReference(path = "Toolbars/File", position = 100),
        @ActionReference(path = "Shortcuts", name = "D-N"),
        @ActionReference(path = FileNode.ACTIONS_FOLDER, position = 200)})
    public static final class NewFileAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            create(e, false);
        }
    }

    @ActionID(category = "File", id = "org.webbeans.workbench.files.NewFolderAction")
    @ActionRegistration(displayName = "#CTL_NewFolder", iconBase = "org/webbeans/workbench/files/icons/folder.svg")
    @ActionReferences({
        @ActionReference(path = "Menu/File", position = 110, separatorAfter = 150),
        @ActionReference(path = FileNode.ACTIONS_FOLDER, position = 210, separatorAfter = 250)})
    public static final class NewFolderAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            create(e, true);
        }
    }

    /** Asks for a name and creates the file or folder in the selected directory (or the root). */
    static void create(final ActionEvent e, final boolean folder) {
        final ExplorerManager em = explorer(e);
        final Path dir;
        try {
            dir = em != null && FilesTopComponent.targetDirectory(em) != null ? FilesTopComponent.targetDirectory(em)
                    : Lookup.getDefault().lookup(FileService.class).getWorkDir();
        } catch (IOException ex) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Exception(ex));
            return;
        }
        final NotifyDescriptor.InputLine in = new NotifyDescriptor.InputLine(folder ? Bundle.LBL_FolderName() : Bundle.LBL_FileName(),
                folder ? Bundle.TITLE_NewFolder() : Bundle.TITLE_NewFile());
        DialogDisplayer.getDefault().notify(in).thenAccept(new Consumer<Object>() {
            @Override
            public void accept(Object r) {
                if (r != NotifyDescriptor.OK_OPTION) {
                    return;
                }
                String name = in.getInputText() == null ? "" : in.getInputText().trim();
                if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.equals("..")) {
                    DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(Bundle.ERR_BadName(), NotifyDescriptor.ERROR_MESSAGE));
                    return;
                }
                Path target = dir.resolve(name);
                if (Files.exists(target)) {
                    DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(Bundle.ERR_Exists(name), NotifyDescriptor.ERROR_MESSAGE));
                    return;
                }
                try {
                    if (folder) {
                        Files.createDirectory(target);
                    } else {
                        Files.write(target, new byte[0]);
                    }
                } catch (IOException ex) {
                    DialogDisplayer.getDefault().notify(new NotifyDescriptor.Exception(ex));
                    return;
                }
                Lookup.getDefault().lookup(FileService.class).changed(dir);
                Log.info("files", Bundle.MSG_Created(name));
                StatusDisplayer.getDefault().setStatusText(Bundle.MSG_Created(name));
                if (!folder) {
                    EditorTopComponent.openFor(WindowManager.getDefault(), target);
                }
            }
        });
    }

    private static ExplorerManager explorer(ActionEvent e) {
        TopComponent tc = WindowManager.getDefault().findTopComponent("FilesTC");
        return tc instanceof ExplorerManager.Provider ? ((ExplorerManager.Provider) tc).getExplorerManager() : null;
    }

    @ActionID(category = "File", id = "org.webbeans.workbench.files.OpenAction")
    @ActionRegistration(displayName = "#CTL_Open", iconBase = "org/webbeans/workbench/files/icons/text.svg")
    @ActionReference(path = FileNode.ACTIONS_FOLDER, position = 100)
    public static final class OpenAction implements ActionListener {
        private final List<Openable> openables;

        public OpenAction(List<Openable> openables) {
            this.openables = openables;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            for (Openable o : openables) {
                o.open();
            }
        }
    }

    @ActionID(category = "File", id = "org.webbeans.workbench.files.RefreshAction")
    @ActionRegistration(displayName = "#CTL_Refresh", iconBase = "org/webbeans/workbench/files/icons/refresh.svg")
    @ActionReferences({
        @ActionReference(path = "Menu/File", position = 400, separatorBefore = 350),
        @ActionReference(path = "Toolbars/File", position = 400),
        @ActionReference(path = "Shortcuts", name = "F5"),
        @ActionReference(path = FileNode.ACTIONS_FOLDER, position = 300)})
    public static final class RefreshAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            ExplorerManager em = explorer(e);
            FileService fs = Lookup.getDefault().lookup(FileService.class);
            try {
                Path root = fs.getWorkDir();
                fs.changed(root);
                if (em != null) {
                    for (org.webbeans.nodes.Node n : em.getSelectedNodes()) {
                        Path p = n.getLookup().lookup(Path.class);
                        if (p != null && Files.isDirectory(p)) {
                            fs.changed(p);
                        }
                    }
                }
            } catch (IOException ex) {
                DialogDisplayer.getDefault().notify(new NotifyDescriptor.Exception(ex));
                return;
            }
            StatusDisplayer.getDefault().setStatusText(Bundle.MSG_Refreshed());
        }
    }
}
