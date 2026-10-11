package org.webbeans.workbench.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;
import org.webbeans.actions.Action;
import org.webbeans.actions.Actions;
import org.webbeans.nodes.Node;
import org.webbeans.nodes.Openable;
import org.webbeans.test.HeadlessUI;
import org.webbeans.ui.components.TextArea;
import org.webbeans.windows.TopComponent;

/** The files explorer, the editor round trip (open, edit, save, close with a question) and the file actions. */
public class FilesTest {

    @Test
    public void workdirIsSeededAndShownAsNodes() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            FilesTopComponent files = (FilesTopComponent) h.open("FilesTC");
            Path work = h.userDir(DefaultFileService.MODULE_DIR).resolve("workdir");
            assertTrue(Files.exists(work.resolve("README.md")));
            Node root = files.getExplorerManager().getRootContext();
            assertEquals("workdir", root.getName());
            Node[] kids = waitForChildren(root);
            assertEquals("src, notes.txt, README.md", 3, kids.length);
            assertEquals("folders first", "src", kids[0].getName());
            assertNotNull("files are openable", kids[1].getLookup().lookup(Openable.class));
            assertNull("folders are not", kids[0].getLookup().lookup(Openable.class));
            assertEquals(Long.valueOf(Files.size(work.resolve("notes.txt"))), kids[1].getProperty("size").getValue());
        }
    }

    private static Node[] waitForChildren(Node n) throws Exception {
        for (int i = 0; i < 50; i++) {
            Node[] kids = n.getChildren().getNodes();
            if (kids.length > 0 && !"wait".equals(kids[0].getName())) {
                return kids;
            }
            Thread.sleep(100);
        }
        return n.getChildren().getNodes();
    }

    @Test
    public void editorOpensEditsSavesAndAsksBeforeClosing() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            h.open("FilesTC");
            final Path readme = h.userDir(DefaultFileService.MODULE_DIR).resolve("workdir/README.md");
            final EditorTopComponent[] ed = new EditorTopComponent[1];
            h.run(new Runnable() {
                @Override
                public void run() {
                    ed[0] = EditorTopComponent.openFor(h.wm(), readme);
                }
            });
            assertTrue(ed[0].isOpened());
            Action bound = ((org.webbeans.actions.ContextAwareAction) saveAction(h)).createContextAwareInstance(h.globalContext());
            assertEquals("the editor is activated, so Save (over Savable) is enabled", true, bound.isEnabled());
            TextArea area = (TextArea) h.byTestId("editor");
            h.type(area, "# Changed\n");
            assertTrue(ed[0].isDirty());
            assertTrue(ed[0].isModified());
            // Ctrl+S → the Save action over the Savable in the global context
            h.run(new Runnable() {
                @Override
                public void run() {
                    Actions.invoke(saveAction(h), null, h.globalContext());
                }
            });
            assertEquals("# Changed\n", new String(Files.readAllBytes(readme), StandardCharsets.UTF_8));
            assertFalse(ed[0].isDirty());
            // edit again, close → question; "No" closes without saving
            h.type(area, "# Discarded\n");
            h.run(new Runnable() {
                @Override
                public void run() {
                    ed[0].close();
                }
            });
            assertTrue("still open, a dialog asks", ed[0].isOpened());
            assertTrue(h.hasDialog());
            h.answerDialog("dialog-no");
            assertFalse(ed[0].isOpened());
            assertEquals("# Changed\n", new String(Files.readAllBytes(readme), StandardCharsets.UTF_8));
            // the same file opens in the same editor instance
            h.run(new Runnable() {
                @Override
                public void run() {
                    ed[0] = EditorTopComponent.openFor(h.wm(), readme);
                    EditorTopComponent again = EditorTopComponent.openFor(h.wm(), readme);
                    if (again != ed[0]) {
                        throw new AssertionError("second open should reuse the editor");
                    }
                }
            });
        }
    }

    private static Action saveAction(HeadlessUI h) {
        return Actions.forID("File", "org.webbeans.workbench.files.SaveAction");
    }

    @Test
    public void newFileActionAsksForANameCreatesAndOpens() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            h.open("FilesTC");
            final Action create = Actions.forID("File", "org.webbeans.workbench.files.NewFileAction");
            h.run(new Runnable() {
                @Override
                public void run() {
                    Actions.invoke(create, null, h.globalContext());
                }
            });
            assertTrue(h.hasDialog());
            h.type("dialog-input", "todo.txt");
            h.answerDialog("dialog-ok");
            Path file = h.userDir(DefaultFileService.MODULE_DIR).resolve("workdir/todo.txt");
            assertTrue(Files.exists(file));
            boolean open = false;
            for (TopComponent tc : h.wm().getOpened()) {
                if (tc instanceof EditorTopComponent && ((EditorTopComponent) tc).getPath().equals(file)) {
                    open = true;
                }
            }
            assertTrue("the new file opened in an editor", open);
            // a second try with the same name is refused with a message
            h.run(new Runnable() {
                @Override
                public void run() {
                    Actions.invoke(create, null, h.globalContext());
                }
            });
            h.type("dialog-input", "todo.txt");
            h.answerDialog("dialog-ok");
            assertTrue("error message dialog", h.hasDialog());
            h.answerDialog("dialog-ok");
            assertFalse(h.hasDialog());
        }
    }

    @Test
    public void renameAndDeleteGoThroughTheFileSystem() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            FilesTopComponent files = (FilesTopComponent) h.open("FilesTC");
            Path work = h.userDir(DefaultFileService.MODULE_DIR).resolve("workdir");
            Node[] kids = waitForChildren(files.getExplorerManager().getRootContext());
            final Node notes = kids[1];
            h.run(new Runnable() {
                @Override
                public void run() {
                    notes.setName("shopping.txt");
                }
            });
            assertTrue(Files.exists(work.resolve("shopping.txt")));
            assertFalse(Files.exists(work.resolve("notes.txt")));
            h.run(new Runnable() {
                @Override
                public void run() {
                    try {
                        notes.destroy();
                    } catch (java.io.IOException e) {
                        throw new AssertionError(e);
                    }
                }
            });
            assertFalse(Files.exists(work.resolve("shopping.txt")));
        }
    }
}
