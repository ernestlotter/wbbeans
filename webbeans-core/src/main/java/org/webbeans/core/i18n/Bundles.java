package org.webbeans.core.i18n;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.NbBundle;

/**
 * Localised messages with the <em>session</em> locale ({@link LocaleContext}).
 *
 * <p>A package's messages come from two kinds of file that are merged here, at run time:
 * <ul>
 * <li>{@code Bundle.properties} and its {@code Bundle_<locale>.properties} / {@code Bundle_<brand>.properties}
 *     variants — hand-written by the developer, copied into the build output by the build like any other
 *     resource, with NetBeans' lookup order (branding first, then the locale from most to least specific, then
 *     the base file; see {@link #suffixes});</li>
 * <li>{@value #GENERATED_FILE} — written by the annotation processor from the package's {@link Messages}; it
 *     holds the base-locale defaults only and is consulted last.</li>
 * </ul>
 * The hand-written files always win over the generated one, so a translation or a branding override of an
 * {@code @Messages} key goes into {@code Bundle_de.properties} as usual. Because the processor never writes
 * {@code Bundle.properties} itself, a build that copies resources <em>after</em> compiling (NetBeans' Ant projects)
 * or into a separate directory (Gradle) can neither overwrite the generated keys nor lose the hand-written ones,
 * and a package needs no base {@code Bundle.properties} for its {@code Bundle_de.properties} to be found.
 *
 * <p>Files are read as UTF-8 when they decode as such and as ISO-8859-1 otherwise (like NetBeans). Unlike
 * {@code java.util.ResourceBundle} and {@code NbBundle}, the JVM's default locale is not a fallback: a user whose
 * locale has no translation sees the base texts, not the server's.
 *
 * <p>The {@link Messages} annotation generates a {@code Bundle} class per package whose static methods call
 * {@link #getMessage(Class, String, Object...)}.
 */
public final class Bundles {

    private static final Logger LOG = Logger.getLogger(Bundles.class.getName());

    /** The processor's output for a package's {@code @Messages}: {@code <package>/Bundle-generated.properties}. */
    public static final String GENERATED_FILE = "Bundle-generated.properties";

    private static final ResourceBundle NONE = new ListResourceBundleStub();
    /** Bundles per loader, keyed by {@code name|locale|branding} ({@link #NONE} when the package has no files at all). */
    private static final Map<ClassLoader, Map<String, ResourceBundle>> CACHE = new WeakHashMap<ClassLoader, Map<String, ResourceBundle>>();

    private Bundles() {
    }

    /** Message from the bundle of the package of {@code clazz}, formatted with {@link MessageFormat} if arguments are given. */
    public static String getMessage(Class<?> clazz, String key, Object... args) {
        return getMessage(clazz.getClassLoader(), bundleNameOf(clazz), key, LocaleContext.get(), args);
    }

    /** As {@link #getMessage(Class, String, Object...)} with an explicit locale. */
    public static String getMessage(Class<?> clazz, Locale locale, String key, Object... args) {
        return getMessage(clazz.getClassLoader(), bundleNameOf(clazz), key, locale, args);
    }

    /**
     * @param loader     the loader to find the bundle with
     * @param bundleName fully qualified base name, e.g. {@code org.example.accounts.Bundle}
     */
    public static String getMessage(ClassLoader loader, String bundleName, String key, Locale locale, Object... args) {
        String text = getBundle(loader, bundleName, locale).getString(key);
        if (args == null || args.length == 0) {
            return text;
        }
        MessageFormat mf = new MessageFormat(text, locale);
        return mf.format(args);
    }

    /**
     * The bundle for the given locale: the hand-written {@code Bundle*.properties} chain (branding and locale
     * suffixes) backed by the generated {@code @Messages} defaults. Cached per loader.
     *
     * @throws MissingResourceException if the package has neither
     */
    public static ResourceBundle getBundle(ClassLoader loader, String bundleName, Locale locale) {
        ClassLoader l = loader != null ? loader : ClassLoader.getSystemClassLoader();
        Locale loc = locale != null ? locale : Locale.ROOT;
        String branding = getBranding();
        String key = bundleName + '|' + loc + '|' + (branding == null ? "" : branding);
        ResourceBundle bundle;
        synchronized (CACHE) {
            Map<String, ResourceBundle> byKey = CACHE.get(l);
            if (byKey == null) {
                byKey = new HashMap<String, ResourceBundle>();
                CACHE.put(l, byKey);
            }
            bundle = byKey.get(key);
            if (bundle == null) {
                bundle = load(l, bundleName, loc, branding);
                byKey.put(key, bundle == null ? NONE : bundle);
            }
        }
        if (bundle == null || bundle == NONE) {
            throw new MissingResourceException("No such bundle " + bundleName, bundleName, "");
        }
        return bundle;
    }

    /**
     * Resolves a {@code #KEY} reference: returns the localised message, or the value itself if it does not
     * start with {@code #}. A missing key is logged and returned as-is so a UI never breaks over a typo.
     */
    public static String resolve(ClassLoader loader, String bundleName, String value, Locale locale) {
        if (value == null || !value.startsWith("#") || bundleName == null) {
            return value;
        }
        String key = value.substring(1);
        try {
            return getBundle(loader, bundleName, locale).getString(key);
        } catch (MissingResourceException e) {
            LOG.log(Level.WARNING, "No key {0} in bundle {1}", new Object[] {key, bundleName});
            return value;
        }
    }

    /** The message, or null if the package has no bundle or the bundle no such key (nothing is logged). */
    public static String find(ClassLoader loader, String bundleName, String key, Locale locale) {
        try {
            ResourceBundle b = getBundle(loader, bundleName, locale);
            return b.containsKey(key) ? b.getString(key) : null;
        } catch (MissingResourceException e) {
            return null;
        }
    }

    /** {@code org.example.Foo} (or a nested {@code org.example.Foo$Bar}) → {@code org.example.Bundle}. */
    public static String bundleNameOf(Class<?> clazz) {
        String name = clazz.getName();
        int dot = name.lastIndexOf('.');
        return (dot < 0 ? "" : name.substring(0, dot + 1)) + "Bundle";
    }

    /**
     * The branding token: {@code Bundle_<token>.properties} (and {@code Bundle_<token>_<locale>.properties})
     * overlay every bundle. Shared with NetBeans' {@code NbBundle}; set by the launcher's {@code --branding}.
     */
    public static String getBranding() {
        return NbBundle.getBranding();
    }

    /** @see #getBranding() */
    public static void setBranding(String token) {
        NbBundle.setBranding(token);
        clearCache();
    }

    /** Forgets cached bundles (tests, branding changes). */
    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    /**
     * The file-name suffixes to try, most specific first, as NetBeans does: for locale {@code de_DE} and branding
     * {@code my_app} — {@code _my_app_de_DE, _my_app_de, _my_app, _my_de_DE, _my_de, _my, _de_DE, _de} and finally
     * the empty suffix (the base file).
     */
    public static List<String> suffixes(Locale locale, String branding) {
        List<String> locales = new ArrayList<String>();
        String l = locale == null ? "" : locale.toString();
        while (!l.isEmpty()) {
            locales.add("_" + l);
            int u = l.lastIndexOf('_');
            l = u < 0 ? "" : l.substring(0, u);
        }
        locales.add("");
        List<String> out = new ArrayList<String>();
        String b = branding == null || branding.isEmpty() ? "" : "_" + branding;
        while (!b.isEmpty()) {
            for (String s : locales) {
                out.add(b + s);
            }
            int u = b.lastIndexOf('_');
            b = u <= 0 ? "" : b.substring(0, u);
        }
        out.addAll(locales);
        return out;
    }

    private static ResourceBundle load(ClassLoader loader, String bundleName, Locale locale, String branding) {
        String base = bundleName.replace('.', '/');
        int slash = base.lastIndexOf('/');
        String generatedPath = (slash < 0 ? "" : base.substring(0, slash + 1)) + GENERATED_FILE;
        List<Map<String, String>> layers = new ArrayList<Map<String, String>>();
        for (String suffix : suffixes(locale, branding)) {
            Map<String, String> layer = read(loader, base + suffix + ".properties");
            if (layer != null) {
                layers.add(layer);
            }
        }
        Map<String, String> generated = read(loader, generatedPath);
        if (generated != null) {
            layers.add(generated);
        }
        return layers.isEmpty() ? null : new Layered(bundleName, locale, layers);
    }

    /** Every copy of the resource the loader sees, merged (first on the class path wins for a key); null if none. */
    private static Map<String, String> read(ClassLoader loader, String resource) {
        Map<String, String> all = null;
        try {
            for (Enumeration<URL> urls = loader.getResources(resource); urls.hasMoreElements();) {
                URL url = urls.nextElement();
                Properties p = new Properties();
                InputStream in = url.openStream();
                try {
                    p.load(new StringReader(decode(readFully(in))));
                } finally {
                    in.close();
                }
                if (all == null) {
                    all = new LinkedHashMap<String, String>();
                }
                for (String key : p.stringPropertyNames()) {
                    if (!all.containsKey(key)) {
                        all.put(key, p.getProperty(key));
                    }
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot read " + resource, e);
        }
        return all;
    }

    private static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) {
            buf.write(b, 0, n);
        }
        return buf.toByteArray();
    }

    /** UTF-8 if the bytes are valid UTF-8, ISO-8859-1 otherwise. */
    static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    /** Layers from most to least specific; a key comes from the first layer that has it. */
    private static final class Layered extends ResourceBundle {
        private final String name;
        private final Locale locale;
        private final List<Map<String, String>> layers;
        private final Set<String> keys;

        Layered(String name, Locale locale, List<Map<String, String>> layers) {
            this.name = name;
            this.locale = locale;
            this.layers = layers;
            Set<String> all = new LinkedHashSet<String>();
            for (Map<String, String> layer : layers) {
                all.addAll(layer.keySet());
            }
            this.keys = Collections.unmodifiableSet(all);
        }

        @Override
        protected Object handleGetObject(String key) {
            for (Map<String, String> layer : layers) {
                String v = layer.get(key);
                if (v != null) {
                    return v;
                }
            }
            return null;
        }

        @Override
        public Enumeration<String> getKeys() {
            return Collections.enumeration(keys);
        }

        @Override
        protected Set<String> handleKeySet() {
            return keys;
        }

        @Override
        public Locale getLocale() {
            return locale;
        }

        @Override
        public String getBaseBundleName() {
            return name;
        }
    }

    /** Cache marker for "no files". */
    private static final class ListResourceBundleStub extends ResourceBundle {
        @Override
        protected Object handleGetObject(String key) {
            return null;
        }

        @Override
        public Enumeration<String> getKeys() {
            return Collections.emptyEnumeration();
        }
    }
}
