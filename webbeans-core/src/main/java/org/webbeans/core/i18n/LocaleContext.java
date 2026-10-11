package org.webbeans.core.i18n;

import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * The locale of the current thread's work. The session executor sets it to the user's locale for the duration
 * of each task; outside sessions it is the JVM default. {@link Bundles} and the registry read it.
 *
 * <p>NetBeans' own {@code NbBundle.getMessage(Class, key)} is bound to {@code Locale.getDefault()} and therefore
 * cannot be per user; WebBeans code uses {@link Bundles} (and the {@link Messages} annotation, whose generated
 * {@code Bundle} class calls it) instead.
 */
public final class LocaleContext {

    private static final ThreadLocal<Locale> CURRENT = new ThreadLocal<Locale>();

    private LocaleContext() {
    }

    /** The current thread's locale, falling back to {@link Locale#getDefault()}. */
    public static Locale get() {
        Locale l = CURRENT.get();
        return l != null ? l : Locale.getDefault();
    }

    /** Sets the locale for the current thread; null restores the JVM default. Prefer {@link #run}/{@link #call}. */
    public static void set(Locale locale) {
        if (locale == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(locale);
        }
    }

    public static void run(Locale locale, Runnable task) {
        Locale previous = CURRENT.get();
        set(locale);
        try {
            task.run();
        } finally {
            set(previous);
        }
    }

    public static <T> T call(Locale locale, Callable<T> task) throws Exception {
        Locale previous = CURRENT.get();
        set(locale);
        try {
            return task.call();
        } finally {
            set(previous);
        }
    }
}
