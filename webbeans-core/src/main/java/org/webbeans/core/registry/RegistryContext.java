package org.webbeans.core.registry;

import java.util.Locale;
import java.util.Set;

import org.webbeans.core.i18n.LocaleContext;

/**
 * What the registry needs to know about the caller to produce a session view: roles (for {@code roles}
 * filtering), locale (for {@code #KEY} attributes) and the user's hide/reorder overlay.
 *
 * <p>The session executor (step 2) installs a context for the duration of each task with {@link #run}.
 * Without one, the default context applies: all roles, the {@link LocaleContext} locale, no overlay —
 * which is what platform code and tests see.
 */
public abstract class RegistryContext {

    private static final ThreadLocal<RegistryContext> CURRENT = new ThreadLocal<RegistryContext>();

    /** Sees everything; locale from {@link LocaleContext}. */
    public static final RegistryContext ALL = new RegistryContext() {
        @Override
        public Set<String> getRoles() {
            return null;
        }

        @Override
        public Locale getLocale() {
            return LocaleContext.get();
        }

        @Override
        public UserOverlay getOverlay() {
            return null;
        }
    };

    public static RegistryContext current() {
        RegistryContext c = CURRENT.get();
        return c != null ? c : ALL;
    }

    public static void run(RegistryContext context, Runnable task) {
        RegistryContext previous = CURRENT.get();
        CURRENT.set(context);
        try {
            task.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** Sets the context for the current thread; null clears it. Prefer {@link #run}. */
    public static void set(RegistryContext context) {
        if (context == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(context);
        }
    }

    /** The user's roles; {@code null} means unrestricted (every entry visible). */
    public abstract Set<String> getRoles();

    public abstract Locale getLocale();

    /** The user's customisations, or null. */
    public abstract UserOverlay getOverlay();

    /** A context with the given roles and locale and no overlay. */
    public static RegistryContext of(final Set<String> roles, final Locale locale) {
        return of(roles, locale, null);
    }

    public static RegistryContext of(final Set<String> roles, final Locale locale, final UserOverlay overlay) {
        return new RegistryContext() {
            @Override
            public Set<String> getRoles() {
                return roles;
            }

            @Override
            public Locale getLocale() {
                return locale != null ? locale : LocaleContext.get();
            }

            @Override
            public UserOverlay getOverlay() {
                return overlay;
            }
        };
    }
}
