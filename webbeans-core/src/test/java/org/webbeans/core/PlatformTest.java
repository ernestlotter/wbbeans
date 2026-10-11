package org.webbeans.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.webbeans.core.files.DataDir;
import org.webbeans.core.i18n.LocaleContext;
import org.webbeans.core.lookup.RegistryLookup;
import org.webbeans.core.lookup.WbDefaultLookup;
import org.webbeans.core.modules.ModuleInfo;
import org.webbeans.core.modules.ModuleSystem;
import org.webbeans.core.modules.TestModules;
import org.webbeans.core.registry.Entry;
import org.webbeans.core.registry.Registry;
import org.webbeans.core.registry.RegistryContext;

public class PlatformTest {

    private File dir;
    private Platform platform;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("wb-platform").toFile();
    }

    @After
    public void tearDown() {
        if (Platform.getCurrent() != null) {
            Platform.getCurrent().shutdown(true);
        }
        RegistryContext.set(null);
        TestModules.deleteRecursively(dir);
    }

    @Test
    public void defaultLookupIsWebBeansAndPublishesPlatformServices() throws Exception {
        assertTrue(Lookup.getDefault() instanceof WbDefaultLookup);
        File modules = new File(dir, "modules");
        modules.mkdirs();
        new TestModules.Jar("org.api", "1.0").exports("org.api")
                .source("org.api.Greeter", "package org.api; public interface Greeter { String greet(); }")
                .build(modules);
        File api = new File(modules, "org.api-1.0.jar");
        new TestModules.Jar("org.impl", "1.0").dependsOn("org.api").compileAgainst(api)
                .source("org.impl.Hello", "package org.impl; public class Hello implements org.api.Greeter { public String greet() { return \"hello\"; } }")
                .source("org.impl.Registrations", "package org.impl;"
                        + " public class Registrations implements org.webbeans.core.registry.RegistrationProvider {"
                        + "  public void register(org.webbeans.core.registry.RegistrationBuilder b) {"
                        + "   b.instance(\"Services/hello\").supplier(Hello::new).instanceOf(org.api.Greeter.class)"
                        + "     .attr(\"displayName\", \"#CTL_Hello\").bundle(\"org.impl.Bundle\").position(10);"
                        + "   b.instance(\"Menu/Tools/hello\").value(\"tool\").roles(\"admin\");"
                        + "  } }")
                .resource("META-INF/services/org.api.Greeter", "org.impl.Hello\n")
                .resource("META-INF/services/org.webbeans.core.registry.RegistrationProvider", "org.impl.Registrations\n")
                .resource("org/impl/Bundle.properties", "CTL_Hello=Hello service\n")
                .resource("org/impl/Bundle_fr.properties", "CTL_Hello=Service bonjour\n")
                .build(modules);

        platform = Platform.start(new Platform.Config().dataDir(new File(dir, "data").toPath()).moduleDir(modules).scanClasspath(false));

        // platform singletons
        assertSame(platform, Lookup.getDefault().lookup(Platform.class));
        assertSame(platform.getModuleSystem(), ModuleSystem.getDefault());
        assertSame(platform.getRegistry(), Registry.getDefault());
        assertNotNull(Lookup.getDefault().lookup(DataDir.class));
        assertSame(platform.getModuleSystem().getSystemClassLoader(), Lookup.getDefault().lookup(ClassLoader.class));
        assertEquals(Arrays.asList("org.api", "org.impl"), codeNames(platform.getEnabledModules()));

        // @ServiceProvider-style META-INF/services of a module are visible in Lookup.getDefault(),
        // and so are registry "Services" entries: two Greeters, one from each route
        Class<?> greeter = platform.getModuleSystem().getSystemClassLoader().loadClass("org.api.Greeter");
        Collection<?> all = Lookup.getDefault().lookupAll(greeter);
        assertEquals(2, all.size());
        for (Object g : all) {
            assertEquals("hello", greeter.getMethod("greet").invoke(g));
        }

        // registry entries contributed by the module's RegistrationProvider
        Entry e = Registry.getDefault().entry("Services/hello");
        assertNotNull(e);
        assertEquals("org.impl", e.getModule().getCodeName());
        assertEquals("Hello service", e.getDisplayName());
        LocaleContext.set(Locale.FRENCH);
        assertEquals("Service bonjour", e.getDisplayName());
        LocaleContext.set(null);

        // "Services" entries are in the default lookup too, and RegistryLookup.forPath is the forPath equivalent
        assertEquals(1, RegistryLookup.forPath("Services").lookupAll(greeter).size());
        assertSame(e.getInstance(Object.class), RegistryLookup.forPath("Services").lookup(greeter));
        assertEquals(0, RegistryLookup.forPath("Nothing/Here").lookupAll(Object.class).size());
        assertEquals(0, Lookups.forPath("Services").lookupAll(greeter).size()); // NetBeans namedservices: untouched

        // role filtering of registry views
        RegistryContext.set(RegistryContext.of(Collections.singleton("user"), Locale.ENGLISH));
        assertNull(Registry.getDefault().entry("Menu/Tools/hello"));
        RegistryContext.set(RegistryContext.of(Collections.singleton("admin"), Locale.ENGLISH));
        assertEquals("tool", Registry.getDefault().entry("Menu/Tools/hello").getInstance(String.class));

        // data dir layout
        assertTrue(new File(dir, "data/users").isDirectory());
        assertTrue(new File(dir, "data/system").isDirectory());
        File userDir = platform.getDataDir().userFiles("alice").dir("org.impl").toFile();
        assertEquals(new File(dir, "data/users/alice/org.impl"), userDir);
    }

    @Test
    public void shutdownRestoresPlainLookup() throws Exception {
        platform = Platform.start(new Platform.Config().dataDir(new File(dir, "data").toPath()).scanClasspath(false));
        assertNotNull(Lookup.getDefault().lookup(Platform.class));
        assertTrue(platform.shutdown(false));
        platform = null;
        assertNull(Lookup.getDefault().lookup(Platform.class));
    }

    private static java.util.List<String> codeNames(java.util.List<ModuleInfo> modules) {
        java.util.List<String> names = new java.util.ArrayList<String>();
        for (ModuleInfo m : modules) {
            names.add(m.getCodeName());
        }
        return names;
    }
}
