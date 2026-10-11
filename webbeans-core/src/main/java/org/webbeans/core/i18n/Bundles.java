package org.webbeans.core.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.NbBundle;

/**
 * Localised messages with the <em>session</em> locale ({@link LocaleContext}). Bundle lookup, caching, branding
 * ({@code Bundle_<brand>.properties}) and the locale suffix chain are NetBeans' {@link NbBundle#getBundle(String, Locale, ClassLoader)};
 * only the locale selection differs from {@code NbBundle.getMessage}.
 *
 * <p>The {@link Messages} annotation generates a {@code Bundle} class per package whose static methods call
 * {@link #getMessage(Class, String, Object...)}.
 */
public final class Bundles {

    private static final Logger LOG = Logger.getLogger(Bundles.class.getName());

    private Bundles() {
    }

    /** Message from {@code Bundle.properties} in the package of {@code clazz}, formatted with {@link MessageFormat} if arguments are given. */
    public static String getMessage(Class<?> clazz, String key, Object... args) {
        return getMessage(clazz.getClassLoader(), bundleNameOf(clazz), key, LocaleContext.get(), args);
    }

    /** As {@link #getMessage(Class, String, Object...)} with an explicit locale. */
    public static String getMessage(Class<?> clazz, Locale locale, String key, Object... args) {
        return getMessage(clazz.getClassLoader(), bundleNameOf(clazz), key, locale, args);
    }

    /**
     * @param loader     the loader to find the bundle with (a module's loader for module bundles)
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

    /** The bundle for the given locale, via NetBeans' lookup (branding, locale chain, caching). */
    public static ResourceBundle getBundle(ClassLoader loader, String bundleName, Locale locale) {
        return NbBundle.getBundle(bundleName, locale, loader);
    }

    /**
     * Resolves a {@code #KEY} reference: returns the localised message, or the value itself if it does not
     * start with {@code #}. A missing key is logged once and returned as-is so a UI never breaks over a typo.
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

    /** {@code org.example.Foo} (or a nested {@code org.example.Foo$Bar}) → {@code org.example.Bundle}. */
    public static String bundleNameOf(Class<?> clazz) {
        String name = clazz.getName();
        int dot = name.lastIndexOf('.');
        return (dot < 0 ? "" : name.substring(0, dot + 1)) + "Bundle";
    }
}
