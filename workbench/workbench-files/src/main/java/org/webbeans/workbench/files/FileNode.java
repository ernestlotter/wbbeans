package org.webbeans.workbench.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.webbeans.actions.Action;
import org.webbeans.actions.Actions;
import org.webbeans.core.i18n.Messages;
import org.webbeans.nodes.AbstractNode;
import org.webbeans.nodes.Children;
import org.webbeans.nodes.Openable;
import org.webbeans.nodes.PropertySupport;
import org.webbeans.nodes.Sheet;
import org.webbeans.ui.UISession;
import org.webbeans.windows.WindowManager;
import org.webbeans.workbench.core.api.Log;

/**
 * A file or directory as a node: the path and (for files) an {@link Openable} in the lookup, size and modification
 * time as properties, rename and delete through the file system, and the popup actions registered under
 * {@code Files/Actions}.
 */
@Messages({"PROP_Name=Name", "HINT_Name=File name (rename)", "PROP_Size=Size", "HINT_Size=Size in bytes", "PROP_Modified=Modified",
    "HINT_Modified=Last modification", "PROP_Path=Path", "HINT_Path=Location on the server", "MSG_Renamed=Renamed {0} to {1}",
    "MSG_Deleted=Deleted {0}", "ERR_Exists={0} already exists"})
public final class FileNode extends AbstractNode {

    /** Registry folder of the popup actions. */
    public static final String ACTIONS_FOLDER = "Files/Actions";

    private Path path;

    public FileNode(final Path path) {
        super(Files.isDirectory(path) ? Children.create(new FolderChildFactory(path), true) : Children.LEAF, lookupFor(path));
        this.path = path;
        setName(path.getFileName().toString());
        setIconBase(Files.isDirectory(path) ? "org/webbeans/workbench/files/icons/folder.svg" : iconFor(path.getFileName().toString()));
        setShortDescription(path.toString());
        setCanRename(true);
        setCanDestroy(true);
    }

    private static Lookup lookupFor(final Path path) {
        if (Files.isDirectory(path)) {
            return Lookups.singleton(path);
        }
        return Lookups.fixed(path, new Openable() {
            @Override
            public void open() {
                EditorTopComponent.openFor(WindowManager.getDefault(), path);
            }
        });
    }

    static String iconFor(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".java") || n.endsWith(".js") || n.endsWith(".py")) {
            return "org/webbeans/workbench/files/icons/code.svg";
        }
        if (n.endsWith(".md") || n.endsWith(".txt")) {
            return "org/webbeans/workbench/files/icons/text.svg";
        }
        if (n.endsWith(".csv") || n.endsWith(".json") || n.endsWith(".xml")) {
            return "org/webbeans/workbench/files/icons/data.svg";
        }
        return "org/webbeans/workbench/files/icons/file.svg";
    }

    public Path getPath() {
        return path;
    }

    public boolean isDirectory() {
        return Files.isDirectory(path);
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> all = new ArrayList<Action>(Actions.forPath(ACTIONS_FOLDER));
        return all.toArray(new Action[all.size()]);
    }

    @Override
    public void setName(String n) {
        if (path != null && !path.getFileName().toString().equals(n) && n != null && !n.isEmpty() && !n.contains("/")) {
            Path target = path.resolveSibling(n);
            if (Files.exists(target)) {
                throw new IllegalArgumentException(Bundle.ERR_Exists(n));
            }
            try {
                Files.move(path, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
            Log.info("files", Bundle.MSG_Renamed(path.getFileName().toString(), n));
            path = target;
            setShortDescription(path.toString());
            EditorTopComponent.pathRenamed(WindowManager.getDefault(), path.resolveSibling(getName()), target);
        }
        super.setName(n);
    }

    @Override
    public void destroy() throws IOException {
        delete(path);
        Log.info("files", Bundle.MSG_Deleted(path.getFileName().toString()));
        FileService fs = Lookup.getDefault().lookup(FileService.class);
        if (fs != null) {
            fs.changed(path.getParent());
        }
        super.destroy();
    }

    private static void delete(Path p) throws IOException {
        if (Files.isDirectory(p)) {
            try (java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(p)) {
                for (Path c : ds) {
                    delete(c);
                }
            }
        }
        Files.delete(p);
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = new Sheet();
        Sheet.Set set = Sheet.createPropertiesSet();
        set.put(new PropertySupport.Name(this, Bundle.PROP_Name(), Bundle.HINT_Name()));
        set.put(new PropertySupport.ReadOnly<Long>("size", Long.class, Bundle.PROP_Size(), Bundle.HINT_Size()) {
            @Override
            public Long getValue() {
                try {
                    return Files.isDirectory(path) ? null : Files.size(path);
                } catch (IOException e) {
                    return null;
                }
            }
        });
        set.put(new PropertySupport.ReadOnly<String>("modified", String.class, Bundle.PROP_Modified(), Bundle.HINT_Modified()) {
            @Override
            public String getValue() {
                try {
                    UISession s = UISession.current();
                    DateFormat f = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, s != null ? s.getLocale() : java.util.Locale.getDefault());
                    return f.format(new Date(Files.getLastModifiedTime(path).toMillis()));
                } catch (IOException e) {
                    return "";
                }
            }
        });
        set.put(new PropertySupport.ReadOnly<String>("path", String.class, Bundle.PROP_Path(), Bundle.HINT_Path()) {
            @Override
            public String getValue() {
                return path.toString();
            }
        });
        sheet.put(set);
        return sheet;
    }

    /** Refreshes the size/modified properties after a save. */
    void changed() {
        firePropertyChange("size", null, null);
        firePropertyChange("modified", null, null);
    }
}
