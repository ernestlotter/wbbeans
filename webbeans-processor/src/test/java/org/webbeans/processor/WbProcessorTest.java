package org.webbeans.processor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.After;
import org.junit.Test;
import org.webbeans.core.i18n.LocaleContext;
import org.webbeans.core.registry.Entry;
import org.webbeans.core.registry.RegistrationBuilder;
import org.webbeans.core.registry.RegistrationProvider;
import org.webbeans.core.registry.Registry;
import org.webbeans.core.registry.RegistryContext;

public class WbProcessorTest {

    @After
    public void reset() {
        LocaleContext.set(null);
        RegistryContext.set(null);
    }

    private static Registry registryOf(Compile c, String providerClass) throws Exception {
        ClassLoader loader = c.loader();
        RegistrationProvider p = (RegistrationProvider) loader.loadClass(providerClass).getDeclaredConstructor().newInstance();
        RegistrationBuilder b = new RegistrationBuilder(null, loader);
        p.register(b);
        Registry r = new Registry();
        r.rebuild(Collections.singletonList(b));
        return r;
    }

    @Test
    public void registrationOnClassAndMethodWithReferencesAndMessages() throws Exception {
        Compile c = Compile.run(Collections.singletonList("-Awebbeans.module=org.example.accounts"),
                "org.example.accounts.SaveAction",
                "package org.example.accounts;\n"
                + "import org.webbeans.core.registry.Registration;\n"
                + "import org.webbeans.core.i18n.Messages;\n"
                + "@Registration(path = \"Actions/File\", position = 300, type = Runnable.class,\n"
                + "    attrs = {\"displayName=#CTL_Save\", \"async=true\", \"weight=7\", \"icon=org/example/save.svg\"},\n"
                + "    roles = {\"admin\", \"accountant\"},\n"
                + "    references = {@Registration.Reference(path = \"Menu/File\", position = 300, separatorAfter = 350),\n"
                + "                  @Registration.Reference(path = \"Toolbars/File\", position = 100)})\n"
                + "@Messages({\"CTL_Save=Save\", \"# a comment\", \"MSG_Saved={0} saved in {1} ms\"})\n"
                + "public class SaveAction implements Runnable {\n"
                + "    public void run() { }\n"
                + "    public String text() { return Bundle.CTL_Save() + \"/\" + Bundle.MSG_Saved(\"x\", 12); }\n"
                + "}\n",
                "org.example.accounts.util.Factories",
                "package org.example.accounts.util;\n"
                + "import org.webbeans.core.registry.Registration;\n"
                + "import org.webbeans.core.registry.Entry;\n"
                + "public class Factories {\n"
                + "    @Registration(path = \"Services\", name = \"answer\", attrs = \"displayName=The answer\")\n"
                + "    public static Integer answer() { return 42; }\n"
                + "    @Registration(path = \"Services\")\n"
                + "    public static String echo(Entry e) { return e.getPath(); }\n"
                + "}\n",
                "res:org/example/accounts/Bundle.properties", "CTL_Existing=From source\n",
                "res:org/example/accounts/Bundle_de.properties", "CTL_Save=Speichern\n");
        assertTrue(c.log, c.ok);

        // generated provider in the module package
        assertTrue(c.exists("org/example/accounts/generated/WbRegistrations.class"));
        assertEquals("org.example.accounts.generated.WbRegistrations",
                c.read("META-INF/services/org.webbeans.core.registry.RegistrationProvider").trim());

        Registry r = registryOf(c, "org.example.accounts.generated.WbRegistrations");
        Entry save = r.entry("Actions/File/org-example-accounts-SaveAction");
        assertNotNull(r.paths().toString(), save);
        assertEquals(300, save.getPosition());
        assertEquals(Runnable.class.getName(), save.getInstanceOf().getName());
        assertEquals("Save", save.getDisplayName());
        assertEquals(Boolean.TRUE, save.getAttributes().get("async"));
        assertEquals(Integer.valueOf(7), save.getAttributes().get("weight"));
        assertEquals("org/example/save.svg", save.getString("icon"));
        assertEquals(new java.util.LinkedHashSet<String>(Arrays.asList("admin", "accountant")), save.getRoles());
        assertTrue(save.getInstance(Runnable.class) instanceof Runnable);
        assertTrue(save.getInstance(Object.class) == save.getInstance(Object.class));

        // references and separators
        List<Entry> menu = r.folder("Menu/File").getEntries();
        assertEquals(2, menu.size());
        assertEquals(Entry.Kind.REFERENCE, menu.get(0).getKind());
        assertEquals("Save", menu.get(0).getDisplayName());
        assertEquals(Entry.Kind.SEPARATOR, menu.get(1).getKind());
        assertEquals(350, menu.get(1).getPosition());
        assertEquals(1, r.instances("Toolbars/File", Runnable.class).size());

        // method factories
        assertEquals(Integer.valueOf(42), r.entry("Services/answer").getInstance(Integer.class));
        assertEquals("The answer", r.entry("Services/answer").getDisplayName());
        assertEquals("Services/org-example-accounts-util-Factories-echo",
                r.entry("Services/org-example-accounts-util-Factories-echo").getInstance(String.class));

        // messages: Bundle class and merged properties, session locale aware
        Object action = save.getInstance(Object.class);
        assertEquals("Save/x saved in 12 ms", action.getClass().getMethod("text").invoke(action));
        LocaleContext.set(Locale.GERMAN);
        assertEquals("Speichern/x saved in 12 ms", action.getClass().getMethod("text").invoke(action));
        LocaleContext.set(null);
        Map<String, String> props = Compile.properties(c.read("org/example/accounts/Bundle.properties"));
        assertEquals("From source", props.get("CTL_Existing"));
        assertEquals("Save", props.get("CTL_Save"));
        assertEquals("{0} saved in {1} ms", props.get("MSG_Saved"));
        assertTrue(c.read("org/example/accounts/Bundle.properties").contains("# a comment"));

        // the registry view respects the session locale for #KEY attributes too
        RegistryContext.set(RegistryContext.of(null, Locale.GERMAN));
        assertEquals("Speichern", r.entry("Actions/File/org-example-accounts-SaveAction").getDisplayName());
    }

    @Test
    public void modulePackageDefaultsToCommonPrefixAndHideRenderer() throws Exception {
        Compile c = Compile.run(Collections.<String>emptyList(),
                "com.acme.ui.Gauge",
                "package com.acme.ui;\n"
                + "@org.webbeans.core.registry.Renderer(id = \"acme.Gauge\", resource = \"com/acme/ui/gauge.js\", css = \"com/acme/ui/gauge.css\")\n"
                + "public class Gauge { }\n",
                "com.acme.other.package-info",
                "@org.webbeans.core.registry.Hide({\"Menu/Tools/old\", \"Toolbars/Debug\"})\n"
                + "package com.acme.other;\n");
        assertTrue(c.log, c.ok);
        assertTrue(c.exists("com/acme/generated/WbRegistrations.class"));
        Registry r = registryOf(c, "com.acme.generated.WbRegistrations");
        Entry gauge = r.entry("Renderers/acme.Gauge");
        assertNotNull(gauge);
        assertEquals("com/acme/ui/gauge.js", gauge.getString("resource"));
        assertEquals("com/acme/ui/gauge.css", gauge.getString("css"));
        assertEquals("com.acme.ui.Gauge", gauge.getString("component"));
        assertEquals("com.acme.ui.Gauge", gauge.getInstance(Class.class).getName());

        RegistrationBuilder other = new RegistrationBuilder(null, getClass().getClassLoader());
        other.instance("Menu/Tools/old").value("old");
        other.instance("Menu/Tools/new").value("new");
        other.instance("Toolbars/Debug/step").value("step");
        RegistrationBuilder generated = new RegistrationBuilder(null, c.loader());
        ((RegistrationProvider) c.loader().loadClass("com.acme.generated.WbRegistrations").getDeclaredConstructor().newInstance())
                .register(generated);
        Registry merged = new Registry();
        merged.rebuild(Arrays.asList(other, generated));
        assertEquals(Collections.singletonList("new"), merged.instances("Menu/Tools", String.class));
        assertTrue(merged.instances("Toolbars/Debug", String.class).isEmpty());
    }

    @Test
    public void nothingToGenerateProducesNoProvider() throws Exception {
        Compile c = Compile.run(Collections.<String>emptyList(),
                "org.plain.Nothing", "package org.plain; public class Nothing { }\n");
        assertTrue(c.log, c.ok);
        assertNull(c.read("META-INF/services/org.webbeans.core.registry.RegistrationProvider"));
    }

    @Test
    public void errorsAreReportedAtTheElement() throws Exception {
        Compile c = Compile.run(Collections.<String>emptyList(),
                "org.bad.NotPublic",
                "package org.bad;\n"
                + "@org.webbeans.core.registry.Registration(path = \"X\")\n"
                + "class NotPublic { }\n",
                "org.bad.NoCtor",
                "package org.bad;\n"
                + "@org.webbeans.core.registry.Registration(path = \"X\")\n"
                + "public class NoCtor { public NoCtor(int x) { } }\n",
                "org.bad.Dup",
                "package org.bad;\n"
                + "@org.webbeans.core.registry.Registration(path = \"X\", name = \"same\")\n"
                + "public class Dup { }\n",
                "org.bad.Dup2",
                "package org.bad;\n"
                + "@org.webbeans.core.registry.Registration(path = \"X\", name = \"same\")\n"
                + "public class Dup2 { }\n",
                "org.bad.BadAttr",
                "package org.bad;\n"
                + "@org.webbeans.core.registry.Registration(path = \"Y\", attrs = \"novalue\")\n"
                + "@org.webbeans.core.i18n.Messages({\"not a key=1\", \"novalue\"})\n"
                + "public class BadAttr { }\n");
        assertFalse(c.ok);
        List<String> errors = c.errors();
        assertTrue(errors.toString(), contains(errors, "Must be public"));
        assertTrue(errors.toString(), contains(errors, "public no-argument constructor"));
        assertTrue(errors.toString(), contains(errors, "already registered"));
        assertTrue(errors.toString(), contains(errors, "key=value"));
        assertTrue(errors.toString(), contains(errors, "Java identifier"));
        assertTrue(errors.toString(), contains(errors, "KEY=value"));
        assertNull("no provider is generated when there are errors",
                c.read("META-INF/services/org.webbeans.core.registry.RegistrationProvider"));
    }

    @Test
    public void messagesOnPackageAndParameterCounts() throws Exception {
        Compile c = Compile.run(Collections.<String>emptyList(),
                "org.msg.package-info",
                "@org.webbeans.core.i18n.Messages({\"PKG_Hello=Hello\", \"PKG_Two={1} and {0,number}\"})\n"
                + "package org.msg;\n",
                "org.msg.User",
                "package org.msg;\n"
                + "public class User { public static String all() { return Bundle.PKG_Hello() + \"|\" + Bundle.PKG_Two(1, 2); } }\n");
        assertTrue(c.log, c.ok);
        assertEquals("Hello|2 and 1", c.loader().loadClass("org.msg.User").getMethod("all").invoke(null));
        assertEquals(0, MessagesSupport.parameterCount("plain"));
        assertEquals(3, MessagesSupport.parameterCount("{2} {0}"));
        assertNull("no registrations: no provider", c.read("META-INF/services/org.webbeans.core.registry.RegistrationProvider"));
    }

    private static boolean contains(List<String> list, String fragment) {
        for (String s : list) {
            if (s.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
