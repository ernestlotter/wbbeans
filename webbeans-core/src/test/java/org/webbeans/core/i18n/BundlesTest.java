package org.webbeans.core.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.TreeSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Hand-written {@code Bundle*.properties} and the processor's {@code Bundle-generated.properties}, merged at run time. */
public class BundlesTest {

    private Path a;
    private Path b;
    private URLClassLoader loader;

    @Before
    public void setUp() throws Exception {
        a = Files.createTempDirectory("wb-bundles-a");
        b = Files.createTempDirectory("wb-bundles-b");
        write(a, "p/q/Bundle.properties", "CTL_Written=By hand\nCTL_Both=Written wins\n");
        write(a, "p/q/Bundle_de.properties", "CTL_Generated=Erzeugt, uebersetzt\n");
        write(a, "p/q/Bundle-generated.properties", "# Generated from @Messages\nCTL_Generated=Generated\nCTL_Both=Generated loses\nMSG_Fmt={0} of {1}\n");
        write(a, "p/q/Bundle_brand.properties", "CTL_Written=Branded\nCTL_Generated=Branded generated\n");
        write(a, "p/q/Bundle_brand_de.properties", "CTL_Written=Markenware\n");
        write(a, "only/gen/Bundle-generated.properties", "K=v\n");
        write(a, "nobase/Bundle-generated.properties", "K=base\n");
        // a UTF-8 file next to ISO-8859-1 ones: each is decoded as what it is
        Files.write(a.resolve("nobase/Bundle_de.properties"), "K=de\nCTL_Utf8=Gr\u00fc\u00dfe\n".getBytes(StandardCharsets.UTF_8));
        write(a, "p/q/Bundle_fr.properties", "CTL_Iso=\u00e9\n");
        write(a, "only/written/Bundle.properties", "K=w\n");
        // a second jar contributing @Messages to the same package (split package): first on the class path wins
        write(b, "p/q/Bundle-generated.properties", "CTL_Generated=From b\nCTL_OnlyB=b\n");
        loader = new URLClassLoader(new URL[] {a.toUri().toURL(), b.toUri().toURL()}, null);
    }

    @After
    public void tearDown() throws Exception {
        loader.close();
        Bundles.clearCache();
        LocaleContext.set(null);
        delete(a.toFile());
        delete(b.toFile());
    }

    @Test
    public void writtenKeysWinOverGeneratedDefaults() {
        ResourceBundle bundle = Bundles.getBundle(loader, "p.q.Bundle", Locale.ENGLISH);
        assertEquals("By hand", bundle.getString("CTL_Written"));
        assertEquals("Written wins", bundle.getString("CTL_Both"));
        assertEquals("Generated", bundle.getString("CTL_Generated"));
        assertEquals("b", bundle.getString("CTL_OnlyB"));
        assertEquals("[CTL_Both, CTL_Generated, CTL_OnlyB, CTL_Written, MSG_Fmt]", new TreeSet<String>(bundle.keySet()).toString());
        assertTrue(bundle.containsKey("MSG_Fmt"));
        assertFalse(bundle.containsKey("nope"));
        assertEquals("1 of 2", Bundles.getMessage(loader, "p.q.Bundle", "MSG_Fmt", Locale.ENGLISH, 1, 2));
    }

    @Test
    public void translationsOfGeneratedKeysComeFromTheWrittenLocaleFiles() {
        assertEquals("Erzeugt, uebersetzt", Bundles.getMessage(loader, "p.q.Bundle", "CTL_Generated", Locale.GERMAN));
        assertEquals("By hand", Bundles.getMessage(loader, "p.q.Bundle", "CTL_Written", Locale.GERMAN));
        assertEquals("Erzeugt, uebersetzt", Bundles.resolve(loader, "p.q.Bundle", "#CTL_Generated", Locale.GERMAN));
        assertEquals("Generated", Bundles.resolve(loader, "p.q.Bundle", "#CTL_Generated", Locale.FRENCH));
    }

    @Test
    public void localeFileWithoutBaseFileIsFound() {
        assertEquals("base", Bundles.getMessage(loader, "nobase.Bundle", "K", Locale.ENGLISH));
        assertEquals("de", Bundles.getMessage(loader, "nobase.Bundle", "K", new Locale("de", "AT")));
        assertEquals("Gr\u00fc\u00dfe", Bundles.getMessage(loader, "nobase.Bundle", "CTL_Utf8", Locale.GERMAN));   // UTF-8 file
        assertEquals("base", Bundles.getMessage(loader, "nobase.Bundle", "K", Locale.ROOT));
        assertEquals("\u00e9", Bundles.getMessage(loader, "p.q.Bundle", "CTL_Iso", Locale.FRENCH));   // ISO-8859-1 file
    }

    @Test
    public void brandingOverlaysEveryLayer() {
        Bundles.setBranding("brand");
        try {
            assertEquals("Branded", Bundles.getMessage(loader, "p.q.Bundle", "CTL_Written", Locale.ENGLISH));
            assertEquals("Branded generated", Bundles.getMessage(loader, "p.q.Bundle", "CTL_Generated", Locale.ENGLISH));
            assertEquals("Markenware", Bundles.getMessage(loader, "p.q.Bundle", "CTL_Written", Locale.GERMAN));
            assertEquals("Branded generated", Bundles.getMessage(loader, "p.q.Bundle", "CTL_Generated", Locale.GERMAN));
            assertEquals("Written wins", Bundles.getMessage(loader, "p.q.Bundle", "CTL_Both", Locale.GERMAN));
        } finally {
            Bundles.setBranding(null);
        }
        assertEquals("By hand", Bundles.getMessage(loader, "p.q.Bundle", "CTL_Written", Locale.ENGLISH));
        assertEquals("[_my_app_de_DE, _my_app_de, _my_app, _my_de_DE, _my_de, _my, _de_DE, _de, ]",
                Bundles.suffixes(Locale.GERMANY, "my_app").toString());
        assertEquals("[]", Bundles.suffixes(Locale.ROOT, null).toString());
    }

    @Test
    public void eitherFileAloneIsEnough() {
        assertEquals("v", Bundles.getMessage(loader, "only.gen.Bundle", "K", Locale.ENGLISH));
        assertEquals("w", Bundles.getMessage(loader, "only.written.Bundle", "K", Locale.ENGLISH));
        assertEquals("#missing", Bundles.resolve(loader, "only.gen.Bundle", "#missing", Locale.ENGLISH));
        try {
            Bundles.getMessage(loader, "only.gen.Bundle", "missing", Locale.ENGLISH);
            fail();
        } catch (MissingResourceException expected) {
            assertEquals("missing", expected.getKey());
        }
        try {
            Bundles.getBundle(loader, "no.such.Bundle", Locale.ENGLISH);
            fail();
        } catch (MissingResourceException expected) {
            // neither file
        }
    }

    private static void write(Path root, String path, String content) throws Exception {
        Path f = root.resolve(path);
        Files.createDirectories(f.getParent());
        Files.write(f, content.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static void delete(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                delete(c);
            }
        }
        f.delete();
    }
}
