package org.webbeans.core.modules;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sees every enabled module (and the platform). Used for reflection by name ({@code Class.forName}),
 * as the thread context class loader on session threads, and as the loader behind
 * {@code Lookups.metaInfServices(...)} in the default Lookup, so that every module's
 * {@code META-INF/services} registrations are visible.
 *
 * <p>Modules themselves never see each other through this loader; their visibility is governed
 * by {@link ModuleClassLoader}. Published in {@code Lookup.getDefault()} as {@code ClassLoader.class}.
 */
public final class SystemClassLoader extends ClassLoader {

    static {
        registerAsParallelCapable();
    }

    private final List<ModuleClassLoader> modules = new CopyOnWriteArrayList<ModuleClassLoader>();

    public SystemClassLoader(ClassLoader platform) {
        super(platform);
    }

    void addModule(ModuleClassLoader loader) {
        modules.add(loader);
    }

    void removeModule(ModuleClassLoader loader) {
        modules.remove(loader);
    }

    public List<ModuleClassLoader> getModuleLoaders() {
        return Collections.unmodifiableList(modules);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                try {
                    c = getParent().loadClass(name);
                } catch (ClassNotFoundException e) {
                    c = null;
                }
            }
            if (c == null) {
                for (ModuleClassLoader m : modules) {
                    if (m.ownsClass(name)) {
                        c = m.loadClass(name);
                        break;
                    }
                }
            }
            if (c == null) {
                throw new ClassNotFoundException(name + " not found in the platform or any enabled module");
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    @Override
    public URL getResource(String name) {
        URL url = getParent().getResource(name);
        if (url != null) {
            return url;
        }
        for (ModuleClassLoader m : modules) {
            url = m.findResource(name);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Set<URL> all = new LinkedHashSet<URL>(Collections.list(getParent().getResources(name)));
        for (ModuleClassLoader m : modules) {
            all.addAll(Collections.list(m.findResources(name)));
        }
        return Collections.enumeration(all);
    }

    @Override
    public String toString() {
        return "SystemClassLoader[" + modules.size() + " modules]";
    }
}
