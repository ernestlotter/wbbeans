package org.webbeans.core.modules;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Class loader of one enabled module.
 *
 * <p>Delegation for classes: (1) already loaded, (2) the platform (parent) loader, (3) the module's own jar
 * and wrapped libraries, (4) each declared dependency's loader, restricted to that module's public packages.
 * Anything else fails with a {@link ClassNotFoundException} whose message names the fix.
 *
 * <p>Resources follow the same order; {@code META-INF/} resources of dependencies are never filtered so
 * {@code ServiceLoader} and NetBeans' {@code MetaInfServicesLookup} work across modules.
 */
public final class ModuleClassLoader extends URLClassLoader {

    /** Lets the loader explain a failed lookup by finding which module owns a class. */
    public interface Universe {
        /** @return the module whose own jar contains the class, or null */
        ModuleInfo findOwner(String className);
    }

    static {
        registerAsParallelCapable();
    }

    private final ModuleInfo info;
    private final List<ModuleClassLoader> dependencies;
    private final Universe universe;

    public ModuleClassLoader(ModuleInfo info, URL[] urls, ClassLoader platform, List<ModuleClassLoader> dependencies,
            Universe universe) {
        super(urls, platform);
        this.info = info;
        this.dependencies = Collections.unmodifiableList(new ArrayList<ModuleClassLoader>(dependencies));
        this.universe = universe;
    }

    public ModuleInfo getModuleInfo() {
        return info;
    }

    public List<ModuleClassLoader> getDependencyLoaders() {
        return dependencies;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                c = loadFromPlatform(name);
            }
            if (c == null) {
                c = loadOwn(name);
            }
            if (c == null) {
                for (ModuleClassLoader dep : dependencies) {
                    c = dep.loadExported(name);
                    if (c != null) {
                        break;
                    }
                }
            }
            if (c == null) {
                throw new ClassNotFoundException(explain(name));
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    private Class<?> loadFromPlatform(String name) {
        try {
            return getParent().loadClass(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private Class<?> loadOwn(String name) {
        try {
            return findClass(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /** Loads a class for a dependent module: only classes in this module's public packages are visible. */
    Class<?> loadExported(String name) {
        if (!info.isPublicPackage(packageOf(name))) {
            return null;
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                c = loadOwn(name);
            }
            if (c == null) {
                // the public package may be re-exported from one of our own dependencies
                for (ModuleClassLoader dep : dependencies) {
                    c = dep.loadExported(name);
                    if (c != null) {
                        break;
                    }
                }
            }
            return c;
        }
    }

    /** Whether this module's own jar (not its dependencies) contains the class. */
    public boolean ownsClass(String className) {
        return findResource(className.replace('.', '/') + ".class") != null;
    }

    private String explain(String name) {
        StringBuilder sb = new StringBuilder(name).append(" not found from module ").append(info.getCodeName());
        ModuleInfo owner = universe == null ? null : universe.findOwner(name);
        if (owner != null) {
            boolean declared = false;
            for (ModuleClassLoader dep : dependencies) {
                if (dep.info == owner) {
                    declared = true;
                    break;
                }
            }
            String pkg = packageOf(name);
            if (!declared) {
                sb.append(": the class is in module ").append(owner.getCodeName())
                        .append(" - add a dependency on it (WebBeans-Module-Dependencies)");
                if (!owner.isPublicPackage(pkg)) {
                    sb.append(" and have it export package ").append(pkg).append(" (WebBeans-Module-Public-Packages)");
                }
            } else if (!owner.isPublicPackage(pkg)) {
                sb.append(": module ").append(owner.getCodeName()).append(" does not export package ").append(pkg)
                        .append(" (WebBeans-Module-Public-Packages)");
            }
        }
        return sb.toString();
    }

    @Override
    public URL getResource(String name) {
        URL url = getParent().getResource(name);
        if (url == null) {
            url = findResource(name);
        }
        if (url == null) {
            for (ModuleClassLoader dep : dependencies) {
                url = dep.getExportedResource(name);
                if (url != null) {
                    break;
                }
            }
        }
        return url;
    }

    URL getExportedResource(String name) {
        if (!isExportedResource(name)) {
            return null;
        }
        URL url = findResource(name);
        if (url == null) {
            for (ModuleClassLoader dep : dependencies) {
                url = dep.getExportedResource(name);
                if (url != null) {
                    break;
                }
            }
        }
        return url;
    }

    private boolean isExportedResource(String name) {
        if (name.startsWith("META-INF/")) {
            return true;
        }
        int slash = name.lastIndexOf('/');
        String pkg = slash < 0 ? "" : name.substring(0, slash).replace('/', '.');
        return info.isPublicPackage(pkg);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Set<URL> all = new LinkedHashSet<URL>();
        all.addAll(Collections.list(getParent().getResources(name)));
        all.addAll(Collections.list(findResources(name)));
        for (ModuleClassLoader dep : dependencies) {
            dep.collectExportedResources(name, all);
        }
        return Collections.enumeration(all);
    }

    private void collectExportedResources(String name, Set<URL> into) throws IOException {
        if (!isExportedResource(name)) {
            return;
        }
        into.addAll(Collections.list(findResources(name)));
        for (ModuleClassLoader dep : dependencies) {
            dep.collectExportedResources(name, into);
        }
    }

    static String packageOf(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? "" : className.substring(0, dot);
    }

    @Override
    public String toString() {
        return "ModuleClassLoader[" + info.getCodeName() + "]";
    }
}
