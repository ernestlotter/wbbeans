package org.webbeans.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.webbeans.actions.Action;
import org.webbeans.actions.Actions;
import org.webbeans.actions.Menu;
import org.webbeans.actions.MenuBar;
import org.webbeans.actions.MenuItem;
import org.webbeans.core.Platform;
import org.webbeans.core.modules.ModuleInfo;
import org.webbeans.core.registry.RegistryContext;
import org.webbeans.nodes.Node;
import org.webbeans.test.HeadlessUI;
import org.webbeans.ui.Component;
import org.webbeans.ui.UISession;
import org.webbeans.windows.MainWindow;
import org.webbeans.windows.TopComponent;

/**
 * The Modules window over a running platform: a module jar built here is installed while an admin session is
 * open; its window opens, its menu item appears, disabling takes both away again; plain users see nothing.
 */
public class ModulesTest {

    private Path dir;
    private Platform platform;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("wb-modules");
        platform = Platform.start(new Platform.Config().dataDir(dir.resolve("data")).scanClasspath(false));
    }

    @After
    public void tearDown() {
        if (Platform.getCurrent() != null) {
            Platform.getCurrent().shutdown(true);
        }
        RegistryContext.set(null);
    }

    private HeadlessUI admin() throws IOException {
        return HeadlessUI.builder().user("alice", "user", "admin").registry(platform.getRegistry())
                .services(platform, platform.getModuleSystem()).dataDir(dir.resolve("data")).start();
    }

    /** The event the server publishes to every session after a registry rebuild. */
    private static void registryChanged(HeadlessUI h) {
        h.session().publish(new UISession.RegistryChangedEvent(false));
        h.run(new Runnable() {
            @Override
            public void run() {
            }
        });
    }

    private static List<String> toolsItems(HeadlessUI h) {
        final List<String> out = new ArrayList<String>();
        final MainWindow main = (MainWindow) h.ui().getContent();
        final Menu[] tools = new Menu[1];
        h.run(new Runnable() {
            @Override
            public void run() {
                MenuBar bar = (MenuBar) main.getSlot(MainWindow.MENUBAR);
                tools[0] = bar.findMenu("Tools");
            }
        });
        if (tools[0] == null) {
            return out;
        }
        h.inject(tools[0], "open", Collections.<String, Object>emptyMap());   // menus fill lazily
        h.run(new Runnable() {
            @Override
            public void run() {
                for (Component c : tools[0].getChildren()) {
                    if (c instanceof MenuItem) {
                        out.add(((MenuItem) c).getText());
                    }
                }
            }
        });
        return out;
    }

    private static String prop(Node n, String name) {
        try {
            for (Node.Property<?> p : n.getPropertySets()[0].getProperties()) {
                if (p.getName().equals(name)) {
                    return String.valueOf(p.getValue());
                }
            }
            return null;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void installShowsUpLiveAndDisableTakesItAway() throws Exception {
        try (final HeadlessUI h = admin()) {
            final ModulesTopComponent tc = (ModulesTopComponent) h.open("ModulesTC");
            assertNotNull(tc);
            assertEquals(0, tc.getExplorerManager().getRootContext().getChildren().getNodes().length);
            assertTrue(toolsItems(h).contains("Modules..."));
            assertFalse(toolsItems(h).contains("Live Hello"));
            assertNull(h.wm().findTopComponent("LiveTC"));

            // install: the platform rebuilds the registry; the server would now publish RegistryChangedEvent
            final ModuleInfo live = platform.installModule(LiveJar.build(dir.resolve("staging"), "1.0"));
            assertTrue(String.valueOf(live.getProblem()), live.isEnabled());
            registryChanged(h);
            h.run(new Runnable() {
                @Override
                public void run() {
                    Node[] nodes = tc.getExplorerManager().getRootContext().getChildren().getNodes();
                    assertEquals(1, nodes.length);
                    assertEquals("live.test", nodes[0].getName());
                    assertEquals("Enabled", prop(nodes[0], "state"));
                    TopComponent liveTc = h.wm().findTopComponent("LiveTC");
                    assertNotNull("the module's start-up window opened", liveTc);
                    assertTrue(liveTc.isOpened());
                    assertNotNull(Actions.forID("Live", "live.HelloAction"));
                }
            });
            assertTrue(toolsItems(h).toString(), toolsItems(h).contains("Live Hello"));

            // disable through the window's action: window closed, menu item gone, node shows Disabled
            h.run(new Runnable() {
                @Override
                public void run() {
                    new ModuleActions.DisableModuleAction(Arrays.asList(live)).actionPerformed(null);
                }
            });
            assertFalse(live.isEnabled());
            registryChanged(h);
            h.run(new Runnable() {
                @Override
                public void run() {
                    assertNull("registration gone with the module", h.wm().findTopComponent("LiveTC"));
                    assertNull(Actions.forID("Live", "live.HelloAction"));
                    Node[] nodes = tc.getExplorerManager().getRootContext().getChildren().getNodes();
                    assertEquals("Disabled", prop(nodes[0], "state"));
                }
            });
            assertFalse(toolsItems(h).contains("Live Hello"));

            // enable again
            h.run(new Runnable() {
                @Override
                public void run() {
                    new ModuleActions.EnableModuleAction(Arrays.asList(live)).actionPerformed(null);
                }
            });
            assertTrue(live.isEnabled());
            registryChanged(h);
            assertTrue(toolsItems(h).contains("Live Hello"));
            h.run(new Runnable() {
                @Override
                public void run() {
                    assertTrue(h.wm().findTopComponent("LiveTC").isOpened());
                }
            });

            // upgrade in place keeps everything consistent (new version shown, one node)
            ModuleInfo v2 = platform.installModule(LiveJar.build(dir.resolve("staging2"), "1.1"));
            assertTrue(v2.isEnabled());
            registryChanged(h);
            h.run(new Runnable() {
                @Override
                public void run() {
                    Node[] nodes = tc.getExplorerManager().getRootContext().getChildren().getNodes();
                    assertEquals(1, nodes.length);
                    assertEquals("1.1", prop(nodes[0], "version"));
                }
            });

            // uninstall: jar gone, node gone
            platform.uninstallModule("live.test");
            registryChanged(h);
            h.run(new Runnable() {
                @Override
                public void run() {
                    assertEquals(0, tc.getExplorerManager().getRootContext().getChildren().getNodes().length);
                    assertNull(h.wm().findTopComponent("LiveTC"));
                }
            });
            assertFalse(Files.exists(platform.getInstallDir().resolve("live.test.jar")));
        }
    }

    @Test
    public void plainUsersGetNeitherWindowNorActions() throws Exception {
        try (final HeadlessUI h = HeadlessUI.builder().user("bob", "user").registry(platform.getRegistry())
                .services(platform, platform.getModuleSystem()).dataDir(dir.resolve("data")).start()) {
            final boolean[] seen = new boolean[2];
            h.run(new Runnable() {
                @Override
                public void run() {
                    seen[0] = h.wm().findTopComponent("ModulesTC") != null;
                    for (Action a : Actions.forPath(ModuleNode.ACTIONS_FOLDER)) {
                        if (a != null) {
                            seen[1] = true;
                        }
                    }
                }
            });
            assertFalse(seen[0]);
            assertFalse(seen[1]);
            assertFalse(toolsItems(h).contains("Modules..."));
        }
    }

    // ---- a module jar compiled at test time: one window opened at start-up and one Tools menu action ----------

    static final class LiveJar {
        static Path build(Path work, String version) throws IOException {
            Files.createDirectories(work);
            List<JavaFileObject> sources = new ArrayList<JavaFileObject>();
            sources.add(source("live.LiveTC", "package live;"
                    + " public class LiveTC extends org.webbeans.windows.TopComponent {"
                    + "  public LiveTC() { setDisplayName(\"Live\"); } }"));
            sources.add(source("live.HelloAction", "package live;"
                    + " public class HelloAction implements org.webbeans.actions.ActionListener {"
                    + "  public void actionPerformed(org.webbeans.actions.ActionEvent e) { } }"));
            sources.add(source("live.Registrations", "package live;"
                    + " public class Registrations implements org.webbeans.core.registry.RegistrationProvider {"
                    + "  public void register(org.webbeans.core.registry.RegistrationBuilder b) {"
                    + "   b.instance(\"Windows/Components/LiveTC\").supplier(LiveTC::new).instanceOf(org.webbeans.windows.TopComponent.class)"
                    + "     .attr(\"persistenceType\", Integer.valueOf(1));"
                    + "   b.reference(\"Windows/Modes/editor/LiveTC\", \"Windows/Components/LiveTC\").attr(\"openAtStartup\", Boolean.TRUE).position(50);"
                    + "   b.instance(\"Actions/Live/live-HelloAction\").factory(org.webbeans.actions.Actions::fromEntry)"
                    + "     .instanceOf(org.webbeans.actions.Action.class).attr(\"displayName\", \"Live Hello\").attr(\"delegate\", \"live.HelloAction\");"
                    + "   b.reference(\"Menu/Tools/live-HelloAction\", \"Actions/Live/live-HelloAction\").position(10);"
                    + "  } }"));
            Path classes = work.resolve("classes");
            Files.createDirectories(classes);
            JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
            List<String> opts = Arrays.asList("-d", classes.toString(), "-cp", System.getProperty("java.class.path"), "-proc:none");
            if (!javac.getTask(null, null, null, opts, null, sources).call()) {
                throw new IOException("Cannot compile the test module");
            }
            Path services = classes.resolve("META-INF/services");
            Files.createDirectories(services);
            Files.write(services.resolve("org.webbeans.core.registry.RegistrationProvider"), "live.Registrations\n".getBytes(StandardCharsets.UTF_8));
            Manifest mf = new Manifest();
            mf.getMainAttributes().putValue("Manifest-Version", "1.0");
            mf.getMainAttributes().putValue("WebBeans-Module", "live.test");
            mf.getMainAttributes().putValue("WebBeans-Module-Version", version);
            mf.getMainAttributes().putValue("WebBeans-Module-Display-Name", "Live test module");
            mf.getMainAttributes().putValue("WebBeans-Module-Public-Packages", "-");
            Path jar = work.resolve("live-" + version + ".jar");
            JarOutputStream out = new JarOutputStream(new FileOutputStream(jar.toFile()), mf);
            try {
                addDir(out, classes.toFile(), "");
            } finally {
                out.close();
            }
            return jar;
        }

        private static void addDir(JarOutputStream out, File dir, String prefix) throws IOException {
            File[] files = dir.listFiles();
            if (files == null) {
                return;
            }
            for (File f : files) {
                String name = prefix + f.getName();
                if (f.isDirectory()) {
                    addDir(out, f, name + "/");
                } else {
                    out.putNextEntry(new JarEntry(name));
                    out.write(Files.readAllBytes(f.toPath()));
                    out.closeEntry();
                }
            }
        }

        private static JavaFileObject source(String fqcn, final String code) {
            return new SimpleJavaFileObject(URI.create("string:///" + fqcn.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return code;
                }
            };
        }
    }
}
