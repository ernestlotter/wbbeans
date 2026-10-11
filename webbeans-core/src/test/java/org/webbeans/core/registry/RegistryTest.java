package org.webbeans.core.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Test;

public class RegistryTest {

    @After
    public void clearContext() {
        RegistryContext.set(null);
    }

    private static List<String> names(List<Entry> entries) {
        List<String> n = new ArrayList<String>();
        for (Entry e : entries) {
            n.add(e.getName());
        }
        return n;
    }

    private static List<String> folderNames(List<Folder> folders) {
        List<String> n = new ArrayList<String>();
        for (Folder f : folders) {
            n.add(f.getName());
        }
        return n;
    }

    @Test
    public void orderingByPositionThenName() {
        RegistrationBuilder b = new RegistrationBuilder(null, getClass().getClassLoader());
        b.instance("Menu/File/z").value("z").position(100);
        b.instance("Menu/File/a").value("a").position(100);
        b.instance("Menu/File/first").value("first").position(10);
        b.instance("Menu/File/last").value("last");
        Registry r = new Registry();
        r.rebuild(Collections.singletonList(b));
        assertEquals(Arrays.asList("first", "a", "z", "last"), names(r.folder("Menu/File").getEntries()));
        assertEquals(Arrays.asList("first", "a", "z", "last"), r.instances("Menu/File", String.class));
        assertTrue(r.folder("Menu").exists());
        assertTrue(r.folder("Menu/File").exists());
        assertFalse(r.folder("Nope").exists());
        assertEquals(Collections.singletonList("File"), folderNames(r.folder("Menu").getFolders()));
        assertEquals(Collections.singletonList("Menu"), folderNames(r.folder("").getFolders()));
    }

    @Test
    public void laterContributionWinsAndHideMasks() {
        RegistrationBuilder base = new RegistrationBuilder(null, getClass().getClassLoader());
        base.instance("Tools/x").value("base-x").position(1);
        base.instance("Tools/y").value("base-y").position(2);
        base.instance("Tools/z").value("base-z").position(3);
        RegistrationBuilder ext = new RegistrationBuilder(null, getClass().getClassLoader());
        ext.instance("Tools/x").value("ext-x").position(1);
        ext.hide("Tools/y");
        Registry r = new Registry();
        r.rebuild(Arrays.asList(base, ext));
        assertEquals(Arrays.asList("ext-x", "base-z"), r.instances("Tools", String.class));
        assertNull(r.entry("Tools/y"));

        RegistrationBuilder hideFolder = new RegistrationBuilder(null, getClass().getClassLoader());
        hideFolder.hide("Tools");
        r.rebuild(Arrays.asList(base, ext, hideFolder));
        assertTrue(r.instances("Tools", String.class).isEmpty());
        assertFalse(r.folder("Tools").exists());
    }

    @Test
    public void referencesFollowTargetsAndSeparators() {
        RegistrationBuilder b = new RegistrationBuilder(null, getClass().getClassLoader());
        b.instance("Actions/File/save").value("SAVE").instanceOf(String.class).attr("displayName", "Save");
        b.reference("Menu/File/save", "Actions/File/save").position(300);
        b.separator("Menu/File").position(350);
        b.reference("Menu/File/dangling", "Actions/File/nope").position(400);
        b.instance("Menu/File/number").value(Integer.valueOf(7)).position(500);
        Registry r = new Registry();
        r.rebuild(Collections.singletonList(b));
        List<Entry> entries = r.folder("Menu/File").getEntries();
        assertEquals(3, entries.size()); // dangling reference is invisible
        assertEquals(Entry.Kind.REFERENCE, entries.get(0).getKind());
        assertEquals("Save", entries.get(0).getDisplayName());
        assertSame(r.entry("Actions/File/save"), entries.get(0).getTarget());
        assertEquals(Entry.Kind.SEPARATOR, entries.get(1).getKind());
        assertEquals(Collections.singletonList("SAVE"), r.instances("Menu/File", String.class));
        assertEquals(Collections.singletonList(7), r.instances("Menu/File", Integer.class));
    }

    @Test
    public void instancesAreSingletonsAndFailuresAreNull() {
        final int[] count = {0};
        RegistrationBuilder b = new RegistrationBuilder(null, getClass().getClassLoader());
        b.instance("Services/counter").supplier(new Supplier<Object>() {
            @Override
            public Object get() {
                return "instance-" + (++count[0]);
            }
        });
        b.instance("Services/broken").supplier(new Supplier<Object>() {
            @Override
            public Object get() {
                throw new IllegalStateException("boom");
            }
        });
        Registry r = new Registry();
        r.rebuild(Collections.singletonList(b));
        Entry e = r.entry("Services/counter");
        assertEquals("instance-1", e.getInstance(String.class));
        assertEquals("instance-1", e.getInstance(String.class));
        assertEquals("instance-2", e.newInstance(String.class));
        assertNull(e.getInstance(Integer.class));
        assertNull(r.entry("Services/broken").getInstance(Object.class));
        assertEquals(Collections.singletonList("instance-1"), r.instances("Services", String.class));
    }

    @Test
    public void instanceOfFiltersWithoutInstantiating() {
        final boolean[] created = {false};
        RegistrationBuilder b = new RegistrationBuilder(null, getClass().getClassLoader());
        b.instance("Services/lazy").instanceOf(Integer.class).supplier(new Supplier<Object>() {
            @Override
            public Object get() {
                created[0] = true;
                return 42;
            }
        });
        Registry r = new Registry();
        r.rebuild(Collections.singletonList(b));
        assertTrue(r.instances("Services", String.class).isEmpty());
        assertFalse(created[0]);
        assertEquals(Collections.singletonList(42), r.instances("Services", Number.class));
        assertTrue(created[0]);
    }

    @Test
    public void rolesFilterPerContext() {
        RegistrationBuilder b = new RegistrationBuilder(null, getClass().getClassLoader());
        b.instance("Windows/admin").value("admin-only").roles("admin");
        b.instance("Windows/staff").value("staff").roles("admin", "accountant");
        b.instance("Windows/all").value("all");
        b.reference("Menu/Window/admin", "Windows/admin");
        Registry r = new Registry();
        r.rebuild(Collections.singletonList(b));
        assertEquals(3, r.folder("Windows").getEntries().size()); // ALL context sees everything

        RegistryContext.set(RegistryContext.of(new HashSet<String>(Collections.singletonList("accountant")), Locale.ENGLISH));
        assertEquals(Arrays.asList("all", "staff"), r.instances("Windows", String.class));
        assertNull(r.entry("Windows/admin"));
        assertTrue("reference to a filtered target is filtered", r.folder("Menu/Window").getEntries().isEmpty());

        RegistryContext.set(RegistryContext.of(new HashSet<String>(Collections.singletonList("admin")), Locale.ENGLISH));
        assertEquals(Arrays.asList("admin-only", "all", "staff"), r.instances("Windows", String.class));
        assertEquals(1, r.folder("Menu/Window").getEntries().size());

        RegistryContext.set(RegistryContext.of(Collections.<String>emptySet(), Locale.ENGLISH));
        assertEquals(Collections.singletonList("all"), r.instances("Windows", String.class));
    }

    @Test
    public void userOverlayHidesAndReorders() {
        RegistrationBuilder b = new RegistrationBuilder(null, getClass().getClassLoader());
        b.instance("Toolbars/Main/new").value("new").position(100);
        b.instance("Toolbars/Main/open").value("open").position(200);
        b.instance("Toolbars/Main/save").value("save").position(300);
        b.folder("Toolbars/Main").position(1);
        b.folder("Toolbars/Debug").position(2);
        Registry r = new Registry();
        r.rebuild(Collections.singletonList(b));

        UserOverlay overlay = new UserOverlay();
        overlay.hide("Toolbars/Main/open");
        overlay.position("Toolbars/Main/save", 50);
        overlay.hide("Toolbars/Debug");
        RegistryContext.set(RegistryContext.of(null, Locale.ENGLISH, overlay));
        assertEquals(Arrays.asList("save", "new"), r.instances("Toolbars/Main", String.class));
        assertEquals(Collections.singletonList("Main"), folderNames(r.folder("Toolbars").getFolders()));

        RegistryContext.set(null);
        assertEquals(Arrays.asList("new", "open", "save"), r.instances("Toolbars/Main", String.class));
        assertEquals(Arrays.asList("Main", "Debug"), folderNames(r.folder("Toolbars").getFolders()));
    }

    @Test
    public void overlayRoundTripsThroughFile() throws Exception {
        UserOverlay o = new UserOverlay();
        o.hide("Menu/Tools/x");
        o.position("Menu/File/y", 42);
        java.nio.file.Path f = java.nio.file.Files.createTempDirectory("wb-overlay").resolve("config/registry.properties");
        o.save(f);
        UserOverlay loaded = UserOverlay.load(f);
        assertTrue(loaded.isHidden("Menu/Tools/x"));
        assertEquals(Integer.valueOf(42), loaded.getPosition("Menu/File/y"));
        assertNull(loaded.getPosition("Menu/Tools/x"));
        assertEquals(o.asMap(), loaded.asMap());
    }

    @Test
    public void bundleKeysResolveWithContextLocale() {
        RegistrationBuilder b = new RegistrationBuilder(null, getClass().getClassLoader());
        b.instance("Menu/File/save").value("x")
                .attr("displayName", "#CTL_Save").attr("plain", "literal").attr("number", 5)
                .bundle("org.webbeans.core.registry.TestBundle");
        b.folder("Menu/File").attr("displayName", "#CTL_FileMenu").bundle("org.webbeans.core.registry.TestBundle");
        Registry r = new Registry();
        r.rebuild(Collections.singletonList(b));

        RegistryContext.set(RegistryContext.of(null, Locale.ENGLISH));
        Entry e = r.entry("Menu/File/save");
        assertEquals("Save", e.getDisplayName());
        assertEquals("literal", e.getString("plain"));
        assertEquals(5, e.getAttributes().get("number"));
        assertEquals("File", r.folder("Menu/File").getDisplayName());

        RegistryContext.set(RegistryContext.of(null, Locale.GERMAN));
        assertEquals("Speichern", r.entry("Menu/File/save").getDisplayName());
        assertEquals("Datei", r.folder("Menu/File").getDisplayName());

        RegistrationBuilder missing = new RegistrationBuilder(null, getClass().getClassLoader());
        missing.instance("X/y").value("x").attr("displayName", "#NOPE").bundle("org.webbeans.core.registry.TestBundle");
        r.rebuild(Collections.singletonList(missing));
        assertEquals("#NOPE", r.entry("X/y").getDisplayName());
    }

    @Test
    public void changeListenerFiresOnRebuild() {
        Registry r = new Registry();
        final int[] fired = {0};
        r.addChangeListener(new javax.swing.event.ChangeListener() {
            @Override
            public void stateChanged(javax.swing.event.ChangeEvent e) {
                fired[0]++;
            }
        });
        r.rebuild(Collections.<RegistrationBuilder>emptyList());
        assertEquals(1, fired[0]);
        assertNotNull(Registry.getDefault());
    }
}
