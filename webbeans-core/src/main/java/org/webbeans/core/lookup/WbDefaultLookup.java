package org.webbeans.core.lookup;

import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

/**
 * The platform's {@code Lookup.getDefault()}. NetBeans finds it through
 * {@code META-INF/services/org.openide.util.Lookup} (see {@link Lookup#getDefault()}); because it also implements
 * {@link Lookup.Provider}, NetBeans asks it for the lookup on every call, which lets the platform
 * {@linkplain #setContent re-target} it once the module system is up.
 *
 * <p>Before the platform starts it delegates to {@code Lookups.metaInfServices} over its own class loader,
 * so unit tests and tools see {@code @ServiceProvider}s on the class path as usual.
 */
public final class WbDefaultLookup extends ProxyLookup implements Lookup.Provider {

    private static volatile WbDefaultLookup instance;

    public WbDefaultLookup() {
        super(Lookups.metaInfServices(WbDefaultLookup.class.getClassLoader()));
        instance = this;
    }

    /** The installed instance, or null if NetBeans picked a different default lookup (e.g. a test mock). */
    public static WbDefaultLookup getInstance() {
        if (instance == null) {
            Lookup.getDefault(); // triggers installation through META-INF/services
        }
        return instance;
    }

    @Override
    public Lookup getLookup() {
        return this;
    }

    /** Replaces the delegates; called by the platform bootstrap and by tests. */
    public void setContent(Lookup... lookups) {
        setLookups(lookups);
    }
}
