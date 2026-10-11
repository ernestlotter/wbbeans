package org.webbeans.help;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.Test;
import org.webbeans.ui.text.Markdown;
import org.webbeans.actions.Actions;
import org.webbeans.dialogs.DialogDisplayer;
import org.webbeans.dialogs.NotifyDescriptor;
import org.webbeans.nodes.Node;
import org.webbeans.test.HeadlessUI;
import org.webbeans.ui.HelpCtx;
import org.webbeans.windows.TopComponent;

/** The help system: Markdown, the table of contents from the registry, the Help window, F1, dialog Help buttons, search, the stand-alone page. */
public class HelpTest {

    @Test
    public void markdownCoversTheDocumentationSubset() {
        String html = Markdown.toHtml(HelpContent.load("org/webbeans/help/test/root.md", null));
        assertTrue(html, html.contains("<h1 id=\"test-root\">Test root</h1>"));
        assertTrue(html, html.contains("<h2 id=\"shortcuts\">Shortcuts</h2>"));
        assertTrue(html, html.contains("<strong>test</strong>"));
        assertTrue(html, html.contains("<a href=\"child.md\">the child page</a>"));
        assertTrue(html, html.contains("<img src=\"pictures/flow.png\" alt=\"diagram\">"));
        assertTrue(html, html.contains("<table>") && html.contains("<th>Key</th>") && html.contains("<td><code>Ctrl+S</code></td>"));
        assertTrue(html, html.contains("<ul>\n<li>first<ul>\n<li>nested item</li>\n</ul>\n</li>\n<li>second with <code>code</code></li>"));
        assertTrue(html, html.contains("<ol>\n<li>one</li>\n<li>two</li>\n</ol>"));
        assertTrue(html, html.contains("<pre><code class=\"language-java\">int x = 1 &lt; 2 ? 3 : 4;\n</code></pre>"));
        assertTrue(html, html.contains("<blockquote>\n<p>A quote with <b>html</b> inside.</p>\n</blockquote>"));
        assertEquals("inline tags pass, stray angle brackets are escaped", "1 &lt; 2 and <kbd>F1</kbd> and a &lt;3",
                Markdown.inline("1 < 2 and <kbd>F1</kbd> and a <3"));
        String child = Markdown.toHtml(HelpContent.load("org/webbeans/help/test/child.md", null));
        assertTrue(child, child.contains("<p class=\"note\">Raw <i>html</i> block.</p>"));
        assertTrue(child, child.contains("<a href=\"https://example.com\">link</a>"));
        assertEquals("3d-viewer", "h-3d-viewer", Markdown.slug("3D viewer"));
    }

    @Test
    public void localisedPageVariantIsPreferred() {
        assertTrue(HelpContent.load("org/webbeans/help/platform.md", Locale.GERMAN).contains("Orientierung"));
        assertTrue(HelpContent.load("org/webbeans/help/platform.md", Locale.ENGLISH).contains("Getting around"));
        assertNull(HelpContent.load("org/webbeans/help/test/missing.md", null));
    }

    @Test
    public void tableOfContentsComesFromTheRegistryWithRoles() throws Exception {
        try (HeadlessUI h = HeadlessUI.builder().user("alice", "user", "admin").start()) {
            final HelpSet[] set = new HelpSet[1];
            h.run(new Runnable() {
                @Override
                public void run() {
                    set[0] = HelpSet.current();
                }
            });
            HelpSet.Topic root = set[0].find("test.root");
            assertNotNull(root);
            assertEquals("test.child", root.getChildren().get(0).getId());
            assertEquals(root, set[0].find("test.child").getParent());
            assertEquals("Test root › Child page", root.getChildren().get(0).getPath().get(0).getTitle() + " › " + root.getChildren().get(0).getTitle());
            assertEquals("test.root", set[0].getHome().getId());
            assertNotNull("alice is admin", set[0].find("test.secret"));
            assertEquals(set[0].find("test.root"), set[0].byResource("org/webbeans/help/test/root.md"));
        }
        try (HeadlessUI h = HeadlessUI.builder().user("bob", "user").start()) {
            final HelpSet[] set = new HelpSet[1];
            h.run(new Runnable() {
                @Override
                public void run() {
                    set[0] = HelpSet.current();
                }
            });
            assertNull("role-filtered", set[0].find("test.secret"));
            assertNotNull(set[0].find("test.root"));
        }
    }

    @Test
    public void displayOpensTheHelpWindowOnThePageAndRewritesLinks() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            final boolean[] shown = new boolean[1];
            h.run(new Runnable() {
                @Override
                public void run() {
                    shown[0] = new HelpCtx("test.child").display();
                }
            });
            assertTrue(shown[0]);
            HelpTopComponent help = (HelpTopComponent) h.wm().findTopComponent("HelpTC");
            assertTrue(help.isOpened());
            assertEquals("test.child", help.getCurrentTopic().getId());
            String html = help.getPageHtml();
            assertTrue(html, html.contains("<a href=\"help:test.root#shortcuts\">the root</a>"));
            assertTrue("external links survive", html.contains("<a href=\"https://example.com\">link</a>"));
            assertTrue("raw html is sanitised but kept", html.contains("<p class=\"note\">Raw <i>html</i> block.</p>"));
            assertEquals("Test root › Child page", ((org.webbeans.ui.components.Label) h.byTestId("help-crumbs")).getText());
            // the root page: a relative image becomes a module resource URL, the link to the child a help: link
            h.run(new Runnable() {
                @Override
                public void run() {
                    new HelpCtx("test.root").display();
                }
            });
            html = help.getPageHtml();
            assertTrue(html, html.contains("<img src=\"wb/res/org/webbeans/help/test/pictures/flow.png\" alt=\"diagram\">"));
            assertTrue(html, html.contains("<a href=\"help:test.child\">the child page</a>"));
            // a missing resource shows a note instead of failing
            h.run(new Runnable() {
                @Override
                public void run() {
                    new HelpCtx("test.other").display();
                }
            });
            assertTrue(help.getPageHtml().contains("wb-help-missing"));
            // an unknown id: contents + status text, display reports false
            h.run(new Runnable() {
                @Override
                public void run() {
                    shown[0] = new HelpCtx("no.such.page").display();
                }
            });
            assertFalse(shown[0]);
            // the default context: the home page (the first top-level topic)
            h.run(new Runnable() {
                @Override
                public void run() {
                    HelpCtx.DEFAULT_HELP.display();
                }
            });
            assertEquals("test.root", help.getCurrentTopic().getId());
        }
    }

    @Test
    public void f1ShowsTheActiveWindowsPageOrTheHome() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            TopComponent with = h.open("HelpTestTC");
            assertEquals(new HelpCtx("test.child"), with.getHelpCtx());
            h.activate(with);
            final org.webbeans.actions.Action f1 = Actions.forID("Help", "org.webbeans.help.ContextHelpAction");
            assertNotNull("F1 registered", f1);
            assertNotNull("Help ▸ Help Contents registered", Actions.forID("Help", "org.webbeans.help.HelpContentsAction"));
            h.run(new Runnable() {
                @Override
                public void run() {
                    Actions.invoke(f1, null, h.globalContext());
                }
            });
            HelpTopComponent help = (HelpTopComponent) h.wm().findTopComponent("HelpTC");
            assertEquals("test.child", help.getCurrentTopic().getId());
            TopComponent without = h.open("HelpTestPlainTC");
            assertTrue(without.getHelpCtx().isDefault());
            h.activate(without);
            h.run(new Runnable() {
                @Override
                public void run() {
                    Actions.invoke(f1, null, h.globalContext());
                }
            });
            assertEquals("home for windows without help", "test.root", help.getCurrentTopic().getId());
            assertEquals("the Help window has its own page", "platform", help.getHelpCtx().getHelpID());
            final org.webbeans.actions.Action contents = Actions.forID("Help", "org.webbeans.help.HelpContentsAction");
            h.run(new Runnable() {
                @Override
                public void run() {
                    Actions.invoke(contents, null, h.globalContext());
                }
            });
            assertTrue("Help Contents shows the table of contents", help.isShowingContents());
        }
    }

    @Test
    public void dialogsWithAHelpContextGetAHelpButton() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            final NotifyDescriptor plain = new NotifyDescriptor.Message("Plain", NotifyDescriptor.INFORMATION_MESSAGE);
            h.run(new Runnable() {
                @Override
                public void run() {
                    DialogDisplayer.getDefault().notify(plain);
                }
            });
            assertTrue(h.hasDialog());
            assertNull("no help button without a context", h.byTestId("dialog-help"));
            h.answerDialog("dialog-ok");
            final NotifyDescriptor withHelp = new NotifyDescriptor.Message("With help", NotifyDescriptor.INFORMATION_MESSAGE);
            withHelp.setHelpCtx(new HelpCtx("test.child"));
            h.run(new Runnable() {
                @Override
                public void run() {
                    DialogDisplayer.getDefault().notify(withHelp);
                }
            });
            assertNotNull(h.byTestId("dialog-help"));
            assertEquals("Help", ((org.webbeans.ui.components.Button) h.byTestId("dialog-help")).getText());
            h.click("dialog-help");
            HelpTopComponent help = (HelpTopComponent) h.wm().findTopComponent("HelpTC");
            assertTrue(help.isOpened());
            assertEquals("test.child", help.getCurrentTopic().getId());
            assertTrue("the dialog stays open", h.hasDialog());
            // the context can change while the dialog is open (the Options dialog does this per category)
            h.run(new Runnable() {
                @Override
                public void run() {
                    withHelp.setHelpCtx(HelpCtx.DEFAULT_HELP);
                }
            });
            assertNull(h.byTestId("dialog-help"));
            h.answerDialog("dialog-ok");
        }
    }

    @Test
    public void searchListsMatchingTopicsFlat() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            HelpTopComponent help = (HelpTopComponent) h.open("HelpTC");
            h.type("help-search", "zebrafish");
            Node[] results = help.getExplorerManager().getRootContext().getChildren().getNodes();
            List<String> ids = new java.util.ArrayList<String>();
            for (Node n : results) {
                ids.add(n.getName());
            }
            Collections.sort(ids);
            assertEquals("[test.child]", ids.toString());   // test.secret shares the resource but needs the admin role
            h.type("help-search", "beta");
            results = help.getExplorerManager().getRootContext().getChildren().getNodes();
            assertEquals(1, results.length);
            assertEquals("keywords match", "test.root", results[0].getName());
            h.type("help-search", "");
            results = help.getExplorerManager().getRootContext().getChildren().getNodes();
            assertTrue("back to the table of contents", results.length >= 3);
            assertEquals("test.root", results[0].getName());
        }
    }

    @Test
    public void standalonePageRendersTocAndContent() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            final String[] html = new String[2];
            h.run(new Runnable() {
                @Override
                public void run() {
                    HelpPageProvider p = new HelpPageProvider();
                    html[0] = p.render(h.session(), "/app", "test.child", Collections.<String, String>emptyMap());
                    html[1] = p.render(h.session(), "", "nope", Collections.<String, String>emptyMap());
                }
            });
            assertNull(html[1]);
            assertTrue(html[0], html[0].startsWith("<!doctype html>"));
            assertTrue(html[0], html[0].contains("<title>Child page</title>"));
            assertTrue(html[0], html[0].contains("<li class=\"current\"><a href=\"/app/wb/help/test.child\">Child page</a>"));
            assertTrue("help: links become page links", html[0].contains("<a href=\"/app/wb/help/test.root#shortcuts\">the root</a>"));
            assertTrue(html[0].contains("<a href=\"/app/wb/help/platform\">Getting around</a>"));
        }
    }

    @Test
    public void restoredHelpWindowShowsAnExpandedTableOfContents() throws Exception {
        java.nio.file.Path data = java.nio.file.Files.createTempDirectory("wb-help");
        try {
            try (HeadlessUI h = HeadlessUI.builder().dataDir(data).start()) {
                h.open("HelpTC");
                h.run(new Runnable() {
                    @Override
                    public void run() {
                        ((org.webbeans.windows.WindowManagerImpl) h.wm()).saveNow();
                    }
                });
            }
            try (HeadlessUI h = HeadlessUI.builder().dataDir(data).start()) {
                HelpTopComponent help = (HelpTopComponent) h.wm().findTopComponent("HelpTC");
                assertTrue("restored with the layout", help.isOpened());
                org.webbeans.ui.Component tree = h.byTestId("help-toc");
                assertNotNull(tree);
                int expands = h.callsOf(tree, "expand").size();
                assertTrue("the table of contents is expanded after a restore: " + expands + " expand calls", expands >= 2);
            }
        } finally {
            deleteTree(data);
        }
    }

    private static void deleteTree(java.nio.file.Path p) throws java.io.IOException {
        if (java.nio.file.Files.isDirectory(p)) {
            try (java.nio.file.DirectoryStream<java.nio.file.Path> ds = java.nio.file.Files.newDirectoryStream(p)) {
                for (java.nio.file.Path c : ds) {
                    deleteTree(c);
                }
            }
        }
        java.nio.file.Files.deleteIfExists(p);
    }
}
