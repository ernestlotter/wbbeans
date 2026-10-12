package org.webbeans.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
import org.webbeans.core.registry.Entry;
import org.webbeans.core.registry.Registry;
import org.webbeans.core.registry.RegistryContext;

/** The platform over a class path: every jar's services, registrations and installers take part. */
public class PlatformTest {

    private File dir;
    private Platform platform;
    private URLClassLoader loader;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("wb-platform").toFile();
    }

    @After
    public void tearDown() throws Exception {
        if (Platform.getCurrent() != null) {
            Platform.getCurrent().shutdown(true);
        }
        RegistryContext.set(null);
        if (loader != null) {
            loader.close();
        }
        TestJars.deleteRecursively(dir);
    }

    @Test
    public void defaultLookupIsWebBeansAndPublishesEverythingOnTheClassPath() throws Exception {
        assertTrue(Lookup.getDefault() instanceof WbDefaultLookup);
        File jars = new File(dir, "jars");
        jars.mkdirs();
        File api = new TestJars.Jar("org.api", "1.0")
                .source("org.api.Greeter", "package org.api; public interface Greeter { String greet(); }")
                .build(jars);
        File impl = new TestJars.Jar("org.impl", "1.0").compileAgainst(api)
                .source("org.impl.Hello", "package org.impl; public class Hello implements org.api.Greeter { public String greet() { return \"hello\"; } }")
                .source("org.impl.Registrations", "package org.impl;"
                        + " public class Registrations implements org.webbeans.core.registry.RegistrationProvider {"
                        + "  public void register(org.webbeans.core.registry.RegistrationBuilder b) {"
                        + "   b.instance(\"Services/hello\").supplier(Hello::new).instanceOf(org.api.Greeter.class)"
                        + "     .attr(\"displayName\", \"#CTL_Hello\").bundle(\"org.impl.Bundle\").position(10);"
                        + "   b.instance(\"Menu/Tools/hello\").value(\"tool\").roles(\"admin\");"
                        + "  } }")
                .source("org.impl.Install", "package org.impl;"
                        + " public class Install extends org.webbeans.core.Installer {"
                        + "  public static final java.util.List<String> LOG = new java.util.ArrayList<String>();"
                        + "  public void restored() { LOG.add(\"restored:\" + (org.openide.util.Lookup.getDefault().lookup(org.api.Greeter.class) != null)); }"
                        + "  public boolean closing() { LOG.add(\"closing\"); return !LOG.contains(\"veto\"); }"
                        + "  public void close() { LOG.add(\"close\"); } }")
                .resource("META-INF/services/org.api.Greeter", "org.impl.Hello\n")
                .resource("META-INF/services/org.webbeans.core.registry.RegistrationProvider", "org.impl.Registrations\n")
                .resource("META-INF/services/org.webbeans.core.Installer", "org.impl.Install\n")
                .resource("org/impl/Bundle.properties", "CTL_Hello=Hello service\n")
                .resource("org/impl/Bundle_fr.properties", "CTL_Hello=Service bonjour\n")
                .build(jars);
        loader = new URLClassLoader(new URL[] {api.toURI().toURL(), impl.toURI().toURL()}, Platform.class.getClassLoader());

        platform = Platform.start(new Platform.Config().dataDir(new File(dir, "data").toPath()).classLoader(loader));

        // platform singletons
        assertSame(platform, Lookup.getDefault().lookup(Platform.class));
        assertSame(platform.getRegistry(), Registry.getDefault());
        assertNotNull(Lookup.getDefault().lookup(DataDir.class));
        assertSame(loader, Lookup.getDefault().lookup(ClassLoader.class));

        // META-INF/services of a jar are visible in Lookup.getDefault(), and so are registry "Services" entries:
        // two Greeters, one from each route
        Class<?> greeter = loader.loadClass("org.api.Greeter");
        Collection<?> all = Lookup.getDefault().lookupAll(greeter);
        assertEquals(2, all.size());
        for (Object g : all) {
            assertEquals("hello", greeter.getMethod("greet").invoke(g));
        }

        // the installer ran after the lookup was complete
        @SuppressWarnings("unchecked")
        List<String> log = (List<String>) loader.loadClass("org.impl.Install").getField("LOG").get(null);
        assertEquals(Collections.singletonList("restored:true"), log);
        assertEquals(1, platform.getInstallers().size());

        // registry entries contributed by the jar's RegistrationProvider
        Entry e = Registry.getDefault().entry("Services/hello");
        assertNotNull(e);
        assertSame(loader, e.getClassLoader());
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
        RegistryContext.set(null);

        // data dir layout
        assertTrue(new File(dir, "data/users").isDirectory());
        assertTrue(new File(dir, "data/system").isDirectory());
        File userDir = platform.getDataDir().userFiles("alice").dir("org.impl").toFile();
        assertEquals(new File(dir, "data/users/alice/org.impl"), userDir);

        // shutdown: closing() then close(); a veto stops it unless forced
        log.add("veto");
        assertFalse(platform.shutdown(false));
        assertEquals("[restored:true, veto, closing]", log.toString());
        assertNotNull(Lookup.getDefault().lookup(Platform.class));
        assertTrue(platform.shutdown(true));
        assertEquals("[restored:true, veto, closing, close]", log.toString());
        assertNull(Lookup.getDefault().lookup(Platform.class));
        platform = null;
    }

    @Test
    public void shutdownRestoresPlainLookup() throws Exception {
        platform = Platform.start(new Platform.Config().dataDir(new File(dir, "data").toPath()));
        assertNotNull(Lookup.getDefault().lookup(Platform.class));
        assertTrue(platform.shutdown(false));
        platform = null;
        assertNull(Lookup.getDefault().lookup(Platform.class));
    }
}
