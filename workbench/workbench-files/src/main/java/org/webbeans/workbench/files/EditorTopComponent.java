package org.webbeans.workbench.files;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Consumer;

import org.openide.util.lookup.Lookups;
import org.webbeans.core.i18n.Messages;
import org.webbeans.dialogs.DialogDisplayer;
import org.webbeans.dialogs.NotifyDescriptor;
import org.webbeans.nodes.Savable;
import org.webbeans.ui.ChangeEvent;
import org.webbeans.ui.Listener;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.TextArea;
import org.webbeans.windows.StatusDisplayer;
import org.webbeans.windows.TopComponent;
import org.webbeans.windows.WindowManager;
import org.webbeans.workbench.core.api.Log;

/**
 * A text editor window per file (multi-instance): a monospace {@link TextArea}, a modified marker in the tab,
 * {@link Savable} and the file's node in the lookup (so File ▸ Save / Ctrl+S and the Properties window work),
 * an asynchronous "save changes?" question on close, and a {@link Factory} that reopens the file at the next
 * login from the state saved with the layout.
 */
@TopComponent.Description(preferredID = "EditorTC", iconBase = "org/webbeans/workbench/files/icons/text.svg", factory = EditorTopComponent.Factory.class, helpId = "files.editor")
@Messages({"MSG_SaveChanges=Save changes to {0}?", "TITLE_Close=Close", "MSG_Saved=Saved {0}", "MSG_Opened=Opened {0}", "LBL_Lines={0} lines, {1} characters"})
public final class EditorTopComponent extends TopComponent {

    private Path path;
    private final FileNode node;
    private final TextArea text = new TextArea();
    private final Label info = new Label();
    private boolean dirty;

    public EditorTopComponent(Path path) {
        this.path = path;
        this.node = new FileNode(path);
        setDisplayName(path.getFileName().toString());
        setToolTipText(path.toString());
        setLayout(new Layout.Border());
        text.setMonospace(true).setTestId("editor");
        text.set(TextArea.WRAP, Boolean.FALSE);
        text.setValue(read(path));
        text.addChangeListener(new Listener<ChangeEvent<String>>() {
            @Override
            public void onEvent(ChangeEvent<String> e) {
                setDirty(true);
                updateInfo();
            }
        });
        text.addInputListener(new Listener<org.webbeans.ui.components.TextField.InputEvent>() {
            @Override
            public void onEvent(org.webbeans.ui.components.TextField.InputEvent e) {
                setDirty(true);
            }
        });
        info.setStyleName("wb-editor-info");
        info.setTestId("editor-info");
        add(text, Layout.Border.CENTER);
        add(info, Layout.Border.SOUTH);
        updateInfo();
        associateLookup(Lookups.fixed(path, node, new Savable() {
            @Override
            public void save() throws IOException {
                doSave();
            }
        }));
    }

    public Path getPath() {
        return path;
    }

    public boolean isDirty() {
        return dirty;
    }

    private void setDirty(boolean m) {
        if (dirty != m) {
            dirty = m;
            setHtmlDisplayName(m ? "<b>" + path.getFileName() + "</b>" : null);
            setModified(m);
        }
    }

    private void updateInfo() {
        String v = text.getValue() == null ? "" : text.getValue();
        int lines = v.isEmpty() ? 0 : v.split("\n", -1).length;
        info.setText(Bundle.LBL_Lines(lines, v.length()));
    }

    void doSave() throws IOException {
        Files.write(path, (text.getValue() == null ? "" : text.getValue()).getBytes(StandardCharsets.UTF_8));
        setDirty(false);
        node.changed();
        StatusDisplayer.getDefault().setStatusText(Bundle.MSG_Saved(path.getFileName().toString()));
        Log.info("files", Bundle.MSG_Saved(path.getFileName().toString()));
    }

    private static String read(Path p) {
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    @Override
    protected boolean canClose() {
        if (!dirty) {
            return true;
        }
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Confirmation(Bundle.MSG_SaveChanges(path.getFileName().toString()),
                Bundle.TITLE_Close(), NotifyDescriptor.YES_NO_CANCEL_OPTION)).thenAccept(new Consumer<Object>() {
            @Override
            public void accept(Object r) {
                if (r == NotifyDescriptor.YES_OPTION) {
                    try {
                        doSave();
                    } catch (IOException e) {
                        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Exception(e));
                        return;
                    }
                    close();                       // canClose passes now: nothing is modified
                } else if (r == NotifyDescriptor.NO_OPTION) {
                    setDirty(false);
                    close();
                }
            }
        });
        return false;
    }

    @Override
    protected void writeState(Map<String, Object> out) {
        out.put("path", path.toString());
    }

    /** Activates the editor of the file if one is open, else opens a new one. */
    public static EditorTopComponent openFor(WindowManager wm, Path path) {
        for (TopComponent tc : wm.getOpened()) {
            if (tc instanceof EditorTopComponent && ((EditorTopComponent) tc).path.equals(path)) {
                tc.requestActive();
                return (EditorTopComponent) tc;
            }
        }
        EditorTopComponent tc = new EditorTopComponent(path);
        tc.open();
        tc.requestActive();
        Log.info("files", Bundle.MSG_Opened(path.getFileName().toString()));
        return tc;
    }

    /** A file was renamed: the open editor follows. */
    static void pathRenamed(WindowManager wm, Path old, Path renamed) {
        for (TopComponent tc : wm.getOpened()) {
            if (tc instanceof EditorTopComponent && ((EditorTopComponent) tc).path.equals(old)) {
                EditorTopComponent e = (EditorTopComponent) tc;
                e.path = renamed;
                e.setDisplayName(renamed.getFileName().toString());
                e.setHtmlDisplayName(e.dirty ? "<b>" + renamed.getFileName() + "</b>" : null);
            }
        }
    }

    /** Recreates editors from the saved layout; files that are gone are skipped. */
    public static final class Factory implements TopComponent.Factory {
        @Override
        public TopComponent create(Map<String, Object> state) {
            Object p = state.get("path");
            if (p == null) {
                return null;
            }
            Path path = Paths.get(String.valueOf(p));
            return Files.isRegularFile(path) ? new EditorTopComponent(path) : null;
        }
    }
}
