package org.webbeans.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openide.util.Lookup;
import org.webbeans.core.modules.ModuleInfo;
import org.webbeans.core.modules.TestModules;
import org.webbeans.core.registry.Registry;
import org.webbeans.core.registry.RegistryContext;

/** Modules installed, upgraded, disabled and removed while the platform runs. */
public class LiveModulesTest {

    private File dir;
    private File staging;
    private Platform platform;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("wb-live").toFile();
        staging = new File(dir, "staging");
        staging.mkdirs();
        platform = Platform.start(new Platform.Config().dataDir(new File(dir, "data").toPath()).scanClasspath(false));
    }

    @After
    public void tearDown() {
        if (Platform.getCurrent() != null) {
            Platform.getCurrent().shutdown(true);
        }
        RegistryContext.set(null);
        TestModules.deleteRecursively(dir);
    }

    /** A module jar registering {@code Services/<name>} with the given value, and exporting its package. */
    private File greeter(String codeName, String version, String value, String dependsOn) throws Exception {
        TestModules.Jar jar = new TestModules.Jar(codeName, version).exports(codeName)
                .source(codeName + ".Registrations", "package " + codeName + ";"
                        + " public class Registrations implements org.webbeans.core.registry.RegistrationProvider {"
                        + "  public void register(org.webbeans.core.registry.RegistrationBuilder b) {"
                        + "   b.instance(\"Services/" + codeName + "\").value(\"" + value + "\");"
                        + "  } }")
                .resource("META-INF/services/org.webbeans.core.registry.RegistrationProvider", codeName + ".Registrations\n");
        if (dependsOn != null) {
            jar.dependsOn(dependsOn);
        }
        File out = new File(staging, codeName + "-" + version);
        out.mkdirs();
        return jar.build(out);
    }

    private static String service(String codeName) {
        org.webbeans.core.registry.Entry e = Registry.getDefault().entry("Services/" + codeName);
        return e == null ? null : e.getInstance(String.class);
    }

    @Test
    public void installUpgradeDisableUninstall() throws Exception {
        final AtomicInteger registryChanges = new AtomicInteger();
        Registry.getDefault().addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                registryChanges.incrementAndGet();
            }
        });
        assertEquals(0, platform.getEnabledModules().size());
        assertNull(service("org.alpha"));

        // install: copied into <datadir>/modules/<codeName>.jar, enabled, registry rebuilt
        ModuleInfo alpha = platform.installModule(greeter("org.alpha", "1.0", "one", null).toPath());
        assertTrue(alpha.isEnabled());
        assertEquals(new File(dir, "data/modules/org.alpha.jar"), alpha.getLocation());
        assertEquals("one", service("org.alpha"));
        assertEquals(1, platform.getEnabledModules().size());
        assertTrue(registryChanges.get() > 0);

        // a dependent module
        ModuleInfo beta = platform.installModule(greeter("org.beta", "1.0", "two", "org.alpha").toPath());
        assertTrue(beta.isEnabled());
        assertEquals("two", service("org.beta"));

        // upgrade alpha in place: beta is disabled for the swap and enabled again on the new version
        ModuleInfo alpha2 = platform.installModule(greeter("org.alpha", "1.1", "uno", null).toPath());
        assertTrue(alpha2.isEnabled());
        assertEquals("1.1", alpha2.getVersion().toString());
        assertEquals("uno", service("org.alpha"));
        assertEquals("two", service("org.beta"));
        List<String> names = new ArrayList<String>();
        for (ModuleInfo m : platform.getEnabledModules()) {
            names.add(m.getCodeName() + "@" + m.getVersion());
        }
        assertEquals("[org.alpha@1.1, org.beta@1.0]", names.toString());
        assertEquals("the old version is forgotten", 2, platform.getModuleSystem().getModules().size());

        // disable alpha: beta goes with it; enable alpha again: beta stays off until asked
        List<ModuleInfo> off = platform.disableModule("org.alpha");
        assertEquals(2, off.size());
        assertNull(service("org.alpha"));
        assertNull(service("org.beta"));
        assertTrue(platform.enableModule("org.alpha").isEnabled());
        assertEquals("uno", service("org.alpha"));
        assertNull(service("org.beta"));
        assertTrue(platform.enableModule("org.beta").isEnabled());
        assertEquals("two", service("org.beta"));

        // uninstall alpha: beta disabled (still known), jar deleted
        platform.uninstallModule("org.alpha");
        assertNull(platform.getModuleSystem().find("org.alpha"));
        assertFalse(new File(dir, "data/modules/org.alpha.jar").exists());
        assertNotNull(platform.getModuleSystem().find("org.beta"));
        assertFalse(platform.getModuleSystem().find("org.beta").isEnabled());
        assertNull(service("org.beta"));
        assertEquals(0, platform.getEnabledModules().size());

        // not a module
        File plain = new File(staging, "plain.jar");
        Files.write(plain.toPath(), new byte[0]);
        try {
            platform.installModule(plain.toPath());
            fail();
        } catch (java.io.IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not a WebBeans module") || e.getMessage().contains("zip"));
        }
        // a disabled module can be uninstalled too; an unknown one cannot
        platform.uninstallModule("org.beta");
        assertNull(platform.getModuleSystem().find("org.beta"));
        assertEquals(0, platform.getInstallDir().toFile().list().length);
        try {
            platform.uninstallModule("org.nothing");
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Unknown module"));
        }
    }

    @Test
    public void installedModulesSurviveARestart() throws Exception {
        platform.installModule(greeter("org.gamma", "1.0", "g", null).toPath());
        platform.shutdown(true);
        platform = Platform.start(new Platform.Config().dataDir(new File(dir, "data").toPath()).scanClasspath(false));
        assertEquals("g", service("org.gamma"));
        assertEquals(1, platform.getEnabledModules().size());
        assertNotNull(Lookup.getDefault().lookup(Platform.class));
    }

    @Test
    public void watcherInstallsDroppedJarsAndRemovesDeletedOnes() throws Exception {
        ModuleWatcher watcher = new ModuleWatcher(platform, 200);
        try {
            Path dropped = platform.getInstallDir().resolve("org.delta.jar");
            Files.copy(greeter("org.delta", "1.0", "d", null).toPath(), dropped, StandardCopyOption.REPLACE_EXISTING);
            waitUntil("installed by the watcher", new java.util.concurrent.Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return "d".equals(service("org.delta"));
                }
            });
            // a jar with another file name is copied to <codeName>.jar; the original stays and is not a second module
            Files.copy(greeter("org.delta", "1.1", "dd", null).toPath(), platform.getInstallDir().resolve("delta-1.1.jar"));
            waitUntil("upgraded by the watcher", new java.util.concurrent.Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return "dd".equals(service("org.delta"));
                }
            });
            assertEquals(1, platform.getEnabledModules().size());
            Files.delete(platform.getInstallDir().resolve("delta-1.1.jar"));
            Files.delete(dropped);
            waitUntil("removed by the watcher", new java.util.concurrent.Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return service("org.delta") == null && platform.getModuleSystem().find("org.delta") == null;
                }
            });
        } finally {
            watcher.close();
        }
    }

    private static void waitUntil(String what, java.util.concurrent.Callable<Boolean> cond) throws Exception {
        long end = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < end) {
            if (Boolean.TRUE.equals(cond.call())) {
                return;
            }
            Thread.sleep(50);
        }
        fail("timeout: " + what);
    }
}
