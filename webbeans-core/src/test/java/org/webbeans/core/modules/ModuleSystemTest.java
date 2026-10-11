package org.webbeans.core.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ModuleSystemTest {

    private File dir;
    private File cache;
    private DefaultModuleSystem system;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("wb-modules").toFile();
        cache = new File(dir, "cache");
        system = new DefaultModuleSystem(ModuleSystemTest.class.getClassLoader(),
                new HashSet<String>(Collections.singletonList("org.webbeans.ui")), cache);
    }

    @After
    public void tearDown() {
        TestModules.deleteRecursively(dir);
    }

    private File apiJar() throws Exception {
        return new TestModules.Jar("org.api", "1.0")
                .exports("org.api.pub")
                .source("org.api.pub.Greeter", "package org.api.pub; public class Greeter { public static String hi() { return org.api.impl.Impl.text(); } }")
                .source("org.api.impl.Impl", "package org.api.impl; public class Impl { public static String text() { return \"hello\"; } }")
                .resource("META-INF/services/org.api.pub.Greeter", "org.api.impl.Impl\n")
                .build(dir);
    }

    @Test
    public void discoversAndEnablesInOrderWithLifecycle() throws Exception {
        File api = apiJar();
        new TestModules.Jar("org.app", "2.3")
                .dependsOn("org.api > 1.0")
                .install("org.app.Installer")
                .compileAgainst(api)
                .source("org.app.Installer", "package org.app; public class Installer extends org.webbeans.core.modules.ModuleInstall {"
                        + " public static final java.util.List<String> LOG = new java.util.ArrayList<String>();"
                        + " public void validate() { LOG.add(\"validate\"); }"
                        + " public void restored() { LOG.add(\"restored:\" + org.api.pub.Greeter.hi()); }"
                        + " public boolean closing() { LOG.add(\"closing\"); return true; }"
                        + " public void close() { LOG.add(\"close\"); } }")
                .build(dir);
        List<ModuleInfo> found = system.discover(dir);
        assertEquals(2, found.size());

        final List<String> events = new ArrayList<String>();
        system.addModuleListener(new ModuleListener.Adapter() {
            @Override
            public void modulesLoaded(List<ModuleInfo> modules) {
                events.add("loaded:" + modules.size());
            }

            @Override
            public void modulesEnabled(List<ModuleInfo> modules) {
                events.add("enabled:" + modules.size());
            }
        });
        system.enableAll();

        List<ModuleInfo> enabled = system.getEnabledModules();
        assertEquals("org.api", enabled.get(0).getCodeName());
        assertEquals("org.app", enabled.get(1).getCodeName());
        assertTrue(enabled.get(1).isEnabled());
        assertNull(enabled.get(1).getProblem());
        assertEquals(Arrays.asList("loaded:2", "enabled:2"), events);

        Class<?> installer = system.getSystemClassLoader().loadClass("org.app.Installer");
        @SuppressWarnings("unchecked")
        List<String> log = (List<String>) installer.getField("LOG").get(null);
        assertEquals(Arrays.asList("validate", "restored:hello"), log);

        assertTrue(system.closing());
        system.close();
        assertEquals(Arrays.asList("validate", "restored:hello", "closing", "close"), log);
    }

    @Test
    public void publicPackagesAreEnforcedBetweenModules() throws Exception {
        File api = apiJar();
        new TestModules.Jar("org.app", "1.0").dependsOn("org.api").compileAgainst(api)
                .source("org.app.Main", "package org.app; public class Main { public String run() { return org.api.pub.Greeter.hi(); } }")
                .build(dir);
        new TestModules.Jar("org.rogue", "1.0").dependsOn("org.api").compileAgainst(api)
                .source("org.rogue.Main", "package org.rogue; public class Main { public String run() { return org.api.impl.Impl.text(); } }")
                .build(dir);
        new TestModules.Jar("org.nodep", "1.0").compileAgainst(api)
                .source("org.nodep.Main", "package org.nodep; public class Main { public String run() { return org.api.pub.Greeter.hi(); } }")
                .build(dir);
        system.discover(dir);
        system.enableAll();
        assertEquals(4, system.getEnabledModules().size());

        Object app = system.find("org.app").getClassLoader().loadClass("org.app.Main").getDeclaredConstructor().newInstance();
        assertEquals("hello", app.getClass().getMethod("run").invoke(app));

        Object rogue = system.find("org.rogue").getClassLoader().loadClass("org.rogue.Main").getDeclaredConstructor().newInstance();
        try {
            rogue.getClass().getMethod("run").invoke(rogue);
            fail("org.api.impl must not be visible");
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            assertTrue(cause.toString(), cause instanceof NoClassDefFoundError);
            String msg = findCause(cause, ClassNotFoundException.class).getMessage();
            assertTrue(msg, msg.contains("does not export package org.api.impl"));
        }

        Object nodep = system.find("org.nodep").getClassLoader().loadClass("org.nodep.Main").getDeclaredConstructor().newInstance();
        try {
            nodep.getClass().getMethod("run").invoke(nodep);
            fail("org.api must not be visible without a dependency");
        } catch (java.lang.reflect.InvocationTargetException e) {
            String msg = findCause(e.getCause(), ClassNotFoundException.class).getMessage();
            assertTrue(msg, msg.contains("add a dependency on it"));
        }
    }

    private static <T extends Throwable> T findCause(Throwable t, Class<T> type) {
        while (t != null && !type.isInstance(t)) {
            t = t.getCause();
        }
        assertNotNull("no " + type.getSimpleName() + " in cause chain", t);
        return type.cast(t);
    }

    @Test
    public void metaInfServicesVisibleThroughSystemLoaderAndDependents() throws Exception {
        File api = apiJar();
        new TestModules.Jar("org.app", "1.0").dependsOn("org.api").compileAgainst(api).build(dir);
        system.discover(dir);
        system.enableAll();
        List<URL> all = Collections.list(system.getSystemClassLoader().getResources("META-INF/services/org.api.pub.Greeter"));
        assertEquals(1, all.size());
        List<URL> fromApp = Collections.list(system.find("org.app").getClassLoader().getResources("META-INF/services/org.api.pub.Greeter"));
        assertEquals(1, fromApp.size());
        // a class owned by a module resolves through the system loader, and to the same Class object
        Class<?> viaSystem = system.getSystemClassLoader().loadClass("org.api.pub.Greeter");
        Class<?> viaApp = system.find("org.app").getClassLoader().loadClass("org.api.pub.Greeter");
        assertSame(viaSystem, viaApp);
    }

    @Test
    public void validateVetoDisablesModuleAndDependents() throws Exception {
        new TestModules.Jar("org.bad", "1.0").install("org.bad.Installer")
                .source("org.bad.Installer", "package org.bad; public class Installer extends org.webbeans.core.modules.ModuleInstall {"
                        + " public void validate() { throw new IllegalStateException(\"no licence\"); } }")
                .build(dir);
        new TestModules.Jar("org.dependent", "1.0").dependsOn("org.bad").build(dir);
        new TestModules.Jar("org.fine", "1.0").build(dir);
        system.discover(dir);
        final List<String> disabled = new ArrayList<String>();
        system.addModuleListener(new ModuleListener.Adapter() {
            @Override
            public void moduleDisabled(ModuleInfo module, Problem problem) {
                disabled.add(module.getCodeName() + ":" + problem.getKind());
            }
        });
        system.enableAll();
        assertEquals(Collections.singletonList("org.fine"), names(system.getEnabledModules()));
        assertEquals(Problem.Kind.INSTALL_FAILED, system.find("org.bad").getProblem().getKind());
        assertTrue(system.find("org.bad").getProblem().getMessage().contains("no licence"));
        assertEquals(Problem.Kind.DEPENDENCY_DISABLED, system.find("org.dependent").getProblem().getKind());
        assertTrue(disabled.toString(), disabled.contains("org.bad:INSTALL_FAILED"));
        assertTrue(disabled.toString(), disabled.contains("org.dependent:DEPENDENCY_DISABLED"));
    }

    @Test
    public void wrappedLibraryJarsAreExtractedAndVisible() throws Exception {
        File libDir = new File(dir, "libsrc");
        libDir.mkdirs();
        File lib = new TestModules.Jar("ignored.lib", "1.0")
                .source("org.lib.Util", "package org.lib; public class Util { public static int answer() { return 42; } }")
                .build(libDir);
        File built = new TestModules.Jar("org.wrapper", "1.0").attr(ModuleInfo.ATTR_CLASS_PATH, "lib/util.jar")
                .compileAgainst(lib)
                .source("org.wrapper.Main", "package org.wrapper; public class Main { public int run() { return org.lib.Util.answer(); } }")
                .build(dir);
        addEntry(built, "lib/util.jar", Files.readAllBytes(lib.toPath()));
        TestModules.deleteRecursively(libDir); // the library is now reachable only through the wrapped jar

        system.discover(dir);
        system.enableAll();
        ModuleInfo wrapper = system.find("org.wrapper");
        assertTrue(String.valueOf(wrapper.getProblem()), wrapper.isEnabled());
        Object main = wrapper.getClassLoader().loadClass("org.wrapper.Main").getDeclaredConstructor().newInstance();
        assertEquals(42, main.getClass().getMethod("run").invoke(main));
        assertTrue(new File(cache, "org.wrapper-1.0/lib/util.jar").isFile());
    }

    private static void addEntry(File jar, String name, byte[] content) throws Exception {
        File tmp = new File(jar.getPath() + ".tmp");
        java.util.jar.JarFile in = new java.util.jar.JarFile(jar);
        java.util.jar.JarOutputStream out = new java.util.jar.JarOutputStream(new java.io.FileOutputStream(tmp), in.getManifest());
        try {
            java.util.Enumeration<java.util.jar.JarEntry> en = in.entries();
            while (en.hasMoreElements()) {
                java.util.jar.JarEntry e = en.nextElement();
                if (e.getName().equals("META-INF/MANIFEST.MF")) {
                    continue;
                }
                out.putNextEntry(new java.util.jar.JarEntry(e.getName()));
                java.io.InputStream s = in.getInputStream(e);
                byte[] buf = new byte[8192];
                int n;
                while ((n = s.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                out.closeEntry();
            }
            out.putNextEntry(new java.util.jar.JarEntry(name));
            out.write(content);
            out.closeEntry();
        } finally {
            out.close();
            in.close();
        }
        assertTrue(jar.delete());
        assertTrue(tmp.renameTo(jar));
    }

    @Test
    public void disableRefusesWhenDependedUponAndWorksOtherwise() throws Exception {
        File api = apiJar();
        new TestModules.Jar("org.app", "1.0").dependsOn("org.api").compileAgainst(api).build(dir);
        system.discover(dir);
        system.enableAll();
        try {
            system.disable(Collections.singletonList(system.find("org.api")));
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("org.app depends on it"));
        }
        system.disable(Collections.singletonList(system.find("org.app")));
        assertEquals(Collections.singletonList("org.api"), names(system.getEnabledModules()));
        assertFalse(system.find("org.app").isEnabled());
        system.disable(Collections.singletonList(system.find("org.api")));
        assertTrue(system.getEnabledModules().isEmpty());
    }

    @Test
    public void enableSubsetPullsDependenciesButNotUnrelatedModules() throws Exception {
        File api = apiJar();
        new TestModules.Jar("org.app", "1.0").dependsOn("org.api").compileAgainst(api).build(dir);
        new TestModules.Jar("org.other", "1.0").build(dir);
        system.discover(dir);
        system.enable(Collections.singletonList(system.find("org.app")));
        assertEquals(Arrays.asList("org.api", "org.app"), names(system.getEnabledModules()));
        assertFalse(system.find("org.other").isEnabled());
        assertNull(system.find("org.other").getProblem());
    }

    private static List<String> names(List<ModuleInfo> list) {
        List<String> names = new ArrayList<String>();
        for (ModuleInfo m : list) {
            names.add(m.getCodeName());
        }
        return names;
    }
}
