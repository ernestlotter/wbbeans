package org.webbeans.workbench.files;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.webbeans.nodes.ChildFactory;
import org.webbeans.nodes.Node;

/**
 * The children of a directory: folders first, then files, both sorted; read on a background thread (the view
 * shows "Please wait…" meanwhile). {@link #refreshDir} re-reads every factory showing a directory after a change.
 */
final class FolderChildFactory extends ChildFactory<Path> {

    private static final Logger LOG = Logger.getLogger(FolderChildFactory.class.getName());
    private static final Map<FolderChildFactory, Boolean> LIVE = Collections.synchronizedMap(new WeakHashMap<FolderChildFactory, Boolean>());

    private final Path dir;

    FolderChildFactory(Path dir) {
        this.dir = dir;
        LIVE.put(this, Boolean.TRUE);
    }

    @Override
    protected boolean createKeys(List<Path> keys) {
        List<Path> dirs = new ArrayList<Path>(), files = new ArrayList<Path>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                (Files.isDirectory(p) ? dirs : files).add(p);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot list " + dir, e);
        }
        Comparator<Path> byName = new Comparator<Path>() {
            @Override
            public int compare(Path a, Path b) {
                return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
            }
        };
        Collections.sort(dirs, byName);
        Collections.sort(files, byName);
        keys.addAll(dirs);
        keys.addAll(files);
        return true;
    }

    @Override
    protected Node createNodeForKey(Path key) {
        return new FileNode(key);
    }

    static void refreshDir(Path d) {
        Set<FolderChildFactory> all;
        synchronized (LIVE) {
            all = new java.util.HashSet<FolderChildFactory>(LIVE.keySet());
        }
        for (FolderChildFactory f : all) {
            if (f.dir.equals(d)) {
                f.refresh(false);
            }
        }
    }
}
