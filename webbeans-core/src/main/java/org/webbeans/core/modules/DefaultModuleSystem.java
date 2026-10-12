package org.webbeans.core.modules;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The standard {@link ModuleSystem}. Construct one, {@link #discover} modules, then {@link #enableAll()} or
 * {@link #enable(Collection)}.
 */
public final class DefaultModuleSystem extends ModuleSystem implements ModuleClassLoader.Universe {

    private static final Logger LOG = Logger.getLogger(DefaultModuleSystem.class.getName());

    private final ClassLoader platform;
    private final Set<String> platformTokens;
    private final File cacheDir;
    private final SystemClassLoader systemLoader;
    private final Map<String, ModuleInfo> modules = new LinkedHashMap<String, ModuleInfo>();
    private final Map<ModuleInfo, ModuleClassLoader> loaders = new LinkedHashMap<ModuleInfo, ModuleClassLoader>();
    private final List<ModuleInfo> enabledOrder = new ArrayList<ModuleInfo>();
    private final List<ModuleListener> listeners = new CopyOnWriteArrayList<ModuleListener>();

    /**
     * @param platform       the loader holding the platform classes (webbeans-core, NetBeans util, Jetty, ...)
     * @param platformTokens capability tokens the platform provides for {@code WebBeans-Module-Requires}
     * @param cacheDir       directory for extracted wrapped library jars (created on demand)
     */
    public DefaultModuleSystem(ClassLoader platform, Set<String> platformTokens, File cacheDir) {
        this.platform = platform;
        this.platformTokens = new HashSet<String>(platformTokens);
        this.cacheDir = cacheDir;
        this.systemLoader = new SystemClassLoader(platform);
    }

    // ---- discovery -------------------------------------------------------------------------------------------

    /** Registers a module descriptor (e.g. from a test or an embedded application). Later duplicates are kept for the resolver to arbitrate. */
    public synchronized void add(ModuleInfo info) {
        String key = info.getCodeName() + "@" + (info.getLocation() != null ? info.getLocation().getPath() : info.getVersion());
        modules.put(key, info);
    }

    /** Forgets a module that is not enabled (after {@link #disable} when its jar goes away). */
    public synchronized boolean remove(ModuleInfo info) {
        if (info.isEnabled()) {
            throw new IllegalStateException("Disable " + info + " before removing it");
        }
        return modules.values().remove(info);
    }

    /** The modules known from the given jar or directory. */
    public synchronized List<ModuleInfo> findByLocation(File location) {
        List<ModuleInfo> out = new ArrayList<ModuleInfo>();
        for (ModuleInfo m : modules.values()) {
            if (m.getLocation() != null && m.getLocation().getAbsoluteFile().equals(location.getAbsoluteFile())) {
                out.add(m);
            }
        }
        return out;
    }

    /** Enabled modules that depend on one of the given ones, directly or indirectly (the given ones excluded). */
    public synchronized List<ModuleInfo> dependents(Collection<ModuleInfo> of) {
        Set<String> names = new HashSet<String>();
        for (ModuleInfo m : of) {
            names.add(m.getCodeName());
        }
        List<ModuleInfo> out = new ArrayList<ModuleInfo>();
        boolean grown = true;
        while (grown) {
            grown = false;
            for (ModuleInfo m : enabledOrder) {
                if (names.contains(m.getCodeName())) {
                    continue;
                }
                for (Dependency d : m.getDependencies()) {
                    if (d.getType() == Dependency.Type.MODULE && names.contains(d.getName())) {
                        names.add(m.getCodeName());
                        out.add(m);
                        grown = true;
                        break;
                    }
                }
            }
        }
        return out;
    }

    /** Scans every {@code *.jar} in the directory (non-recursively) for module manifests. */
    public synchronized List<ModuleInfo> discover(File directory) {
        List<ModuleInfo> found = new ArrayList<ModuleInfo>();
        File[] files = directory.listFiles();
        if (files == null) {
            return found;
        }
        java.util.Arrays.sort(files);
        for (File f : files) {
            if (!f.getName().endsWith(".jar")) {
                continue;
            }
            try {
                JarFile jar = new JarFile(f);
                try {
                    Manifest mf = jar.getManifest();
                    ModuleInfo info = mf == null ? null : ModuleInfo.fromManifest(mf, f);
                    if (info != null) {
                        add(info);
                        found.add(info);
                    }
                } finally {
                    jar.close();
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Cannot read " + f, e);
            } catch (IllegalArgumentException e) {
                LOG.log(Level.WARNING, "Ignoring " + f + ": " + e.getMessage());
            }
        }
        return found;
    }

    /**
     * Finds modules on the platform class path itself (development convenience: {@code wb:run} puts the
     * application's modules on the class path). Visibility rules are not enforced for such modules because
     * the platform loader already sees them; registrations and lifecycle work normally.
     */
    public synchronized List<ModuleInfo> discoverClasspath() {
        List<ModuleInfo> found = new ArrayList<ModuleInfo>();
        try {
            Enumeration<URL> urls = platform.getResources("META-INF/MANIFEST.MF");
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                InputStream in = url.openStream();
                try {
                    Manifest mf = new Manifest(in);
                    ModuleInfo info = ModuleInfo.fromManifest(mf, locationOf(url));
                    if (info != null) {
                        add(info);
                        found.add(info);
                    }
                } catch (IllegalArgumentException e) {
                    LOG.log(Level.WARNING, "Ignoring " + url + ": " + e.getMessage());
                } finally {
                    in.close();
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot scan class path", e);
        }
        return found;
    }

    private static File locationOf(URL manifest) {
        String s = manifest.toExternalForm();
        try {
            if (s.startsWith("jar:") && s.contains("!/")) {
                return new File(new URL(s.substring(4, s.indexOf("!/"))).toURI());
            }
            if (s.startsWith("file:") && s.endsWith("META-INF/MANIFEST.MF")) {
                return new File(new URL(s.substring(0, s.length() - "META-INF/MANIFEST.MF".length())).toURI());
            }
        } catch (Exception e) {
            // fall through
        }
        return null;
    }

    // ---- queries ---------------------------------------------------------------------------------------------

    @Override
    public synchronized Collection<ModuleInfo> getModules() {
        return new ArrayList<ModuleInfo>(modules.values());
    }

    @Override
    public synchronized List<ModuleInfo> getEnabledModules() {
        return new ArrayList<ModuleInfo>(enabledOrder);
    }

    @Override
    public synchronized ModuleInfo find(String codeName) {
        ModuleInfo best = null;
        for (ModuleInfo m : modules.values()) {
            if (m.getCodeName().equals(codeName)) {
                if (m.isEnabled()) {
                    return m;
                }
                if (best == null || m.getVersion().compareTo(best.getVersion()) > 0) {
                    best = m;
                }
            }
        }
        return best;
    }

    @Override
    public SystemClassLoader getSystemClassLoader() {
        return systemLoader;
    }

    @Override
    public synchronized ModuleInfo findOwner(String className) {
        for (ModuleClassLoader l : loaders.values()) {
            if (l.ownsClass(className)) {
                return l.getModuleInfo();
            }
        }
        return null;
    }

    // ---- enable / disable ------------------------------------------------------------------------------------

    /** Enables every known module that can be enabled. */
    public void enableAll() {
        enable(getModules());
    }

    @Override
    public synchronized void enable(Collection<ModuleInfo> batch) {
        Set<ModuleInfo> candidates = new LinkedHashSet<ModuleInfo>(enabledOrder);
        // the batch plus the transitive closure of its dependencies among the known modules
        List<ModuleInfo> queue = new ArrayList<ModuleInfo>(batch);
        while (!queue.isEmpty()) {
            ModuleInfo m = queue.remove(queue.size() - 1);
            if (!m.isEnabled() && isEnabledName(m.getCodeName())) {
                continue; // another version of this module is already running
            }
            if (!candidates.add(m)) {
                continue;
            }
            for (Dependency d : m.getDependencies()) {
                if (d.getType() != Dependency.Type.MODULE) {
                    continue;
                }
                for (ModuleInfo other : modules.values()) {
                    if (other.getCodeName().equals(d.getName()) && !candidates.contains(other)) {
                        queue.add(other);
                    }
                }
            }
        }

        Resolver.Resolution res = new Resolver(platformTokens).resolve(candidates);
        for (Map.Entry<ModuleInfo, Problem> e : res.getProblems().entrySet()) {
            if (!e.getKey().isEnabled()) {
                e.getKey().setProblem(e.getValue());
            }
        }

        List<ModuleInfo> toEnable = new ArrayList<ModuleInfo>();
        for (ModuleInfo m : res.getEnabled()) {
            if (!m.isEnabled()) {
                toEnable.add(m);
            }
        }

        List<ModuleInfo> loaded = new ArrayList<ModuleInfo>();
        Map<ModuleInfo, ModuleInstall> installs = new LinkedHashMap<ModuleInfo, ModuleInstall>();
        for (ModuleInfo m : toEnable) {
            Problem p = dependencyProblem(m);
            if (p == null) {
                p = load(m, installs);
            }
            if (p != null) {
                m.setProblem(p);
            } else {
                loaded.add(m);
            }
        }

        for (ModuleInfo m : batch) {
            if (!m.isEnabled() && m.getProblem() != null) {
                LOG.log(Level.WARNING, "Module {0} disabled: {1}", new Object[] {m, m.getProblem()});
                for (ModuleListener l : listeners) {
                    l.moduleDisabled(m, m.getProblem());
                }
            }
        }
        for (ModuleInfo m : res.getProblems().keySet()) {
            if (!batch.contains(m) && !m.isEnabled() && m.getProblem() != null) {
                LOG.log(Level.FINE, "Module {0} not enabled: {1}", new Object[] {m, m.getProblem()});
            }
        }

        if (loaded.isEmpty()) {
            return;
        }
        List<ModuleInfo> snapshot = Collections.unmodifiableList(new ArrayList<ModuleInfo>(loaded));
        for (ModuleListener l : listeners) {
            l.modulesLoaded(snapshot);
        }
        for (ModuleInfo m : loaded) {
            ModuleInstall install = installs.get(m);
            if (install != null) {
                try {
                    install.restored();
                } catch (RuntimeException e) {
                    LOG.log(Level.SEVERE, "restored() of " + m + " failed", e);
                }
            }
        }
        for (ModuleListener l : listeners) {
            l.modulesEnabled(snapshot);
        }
    }

    private boolean isEnabledName(String codeName) {
        for (ModuleInfo m : enabledOrder) {
            if (m.getCodeName().equals(codeName)) {
                return true;
            }
        }
        return false;
    }

    /** A module whose dependency failed to load in this batch cannot be loaded either. */
    private Problem dependencyProblem(ModuleInfo m) {
        for (Dependency d : m.getDependencies()) {
            if (d.getType() != Dependency.Type.MODULE) {
                continue;
            }
            ModuleInfo target = find(d.getName());
            if (target == null || !target.isEnabled()) {
                return new Problem(Problem.Kind.DEPENDENCY_DISABLED, "Depends on " + d.getName() + " which could not be enabled"
                        + (target != null && target.getProblem() != null ? " (" + target.getProblem() + ")" : ""));
            }
        }
        return null;
    }

    private Problem load(ModuleInfo m, Map<ModuleInfo, ModuleInstall> installs) {
        ModuleClassLoader loader;
        try {
            List<ModuleClassLoader> deps = new ArrayList<ModuleClassLoader>();
            for (Dependency d : m.getDependencies()) {
                if (d.getType() == Dependency.Type.MODULE) {
                    deps.add(loaders.get(find(d.getName())));
                }
            }
            loader = new ModuleClassLoader(m, urlsOf(m), platform, deps, this);
        } catch (IOException e) {
            return new Problem(Problem.Kind.INSTALL_FAILED, "Cannot open module: " + e.getMessage(), e);
        }
        ModuleInstall install = null;
        if (m.getInstallClass() != null) {
            try {
                Class<?> c = loader.loadClass(m.getInstallClass());
                install = (ModuleInstall) c.getDeclaredConstructor().newInstance();
                install.validate();
            } catch (IllegalStateException e) {
                closeQuietly(loader);
                return new Problem(Problem.Kind.INSTALL_FAILED, "validate() vetoed: " + e.getMessage(), e);
            } catch (Exception e) {
                closeQuietly(loader);
                return new Problem(Problem.Kind.INSTALL_FAILED, "Cannot instantiate " + m.getInstallClass() + ": " + e, e);
            }
        }
        loaders.put(m, loader);
        systemLoader.addModule(loader);
        m.setEnabled(true, loader, install);
        enabledOrder.add(m);
        if (install != null) {
            installs.put(m, install);
        }
        return null;
    }

    private URL[] urlsOf(ModuleInfo m) throws IOException {
        List<URL> urls = new ArrayList<URL>();
        File loc = m.getLocation();
        if (loc != null) {
            urls.add(loc.toURI().toURL());
            if (loc.isFile() && !m.getClassPath().isEmpty()) {
                for (String entry : m.getClassPath()) {
                    urls.add(extract(loc, entry, m).toURI().toURL());
                }
            }
        }
        return urls.toArray(new URL[0]);
    }

    /** Extracts a wrapped library jar from the module jar into the cache directory. */
    private File extract(File moduleJar, String entryName, ModuleInfo m) throws IOException {
        File dir = new File(cacheDir, m.getCodeName() + "-" + m.getVersion());
        File target = new File(dir, entryName.replace('/', File.separatorChar));
        if (!target.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) {
            throw new IOException("Illegal class path entry " + entryName);
        }
        JarFile jar = new JarFile(moduleJar);
        try {
            JarEntry entry = jar.getJarEntry(entryName);
            if (entry == null) {
                throw new IOException("Missing wrapped jar " + entryName + " in " + moduleJar);
            }
            if (target.isFile() && target.length() == entry.getSize() && target.lastModified() >= moduleJar.lastModified()) {
                return target;
            }
            target.getParentFile().mkdirs();
            InputStream in = jar.getInputStream(entry);
            try {
                OutputStream out = new FileOutputStream(target);
                try {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                } finally {
                    out.close();
                }
            } finally {
                in.close();
            }
            return target;
        } finally {
            jar.close();
        }
    }

    @Override
    public synchronized void disable(Collection<ModuleInfo> batch) {
        Set<ModuleInfo> set = new HashSet<ModuleInfo>(batch);
        for (ModuleInfo m : enabledOrder) {
            if (set.contains(m)) {
                continue;
            }
            for (Dependency d : m.getDependencies()) {
                if (d.getType() == Dependency.Type.MODULE && set.contains(find(d.getName()))) {
                    throw new IllegalArgumentException("Cannot disable " + d.getName() + ": " + m.getCodeName() + " depends on it");
                }
            }
        }
        List<ModuleInfo> order = new ArrayList<ModuleInfo>();
        for (int i = enabledOrder.size() - 1; i >= 0; i--) {
            ModuleInfo m = enabledOrder.get(i);
            if (set.contains(m)) {
                order.add(m);
            }
        }
        for (ModuleInfo m : order) {
            ModuleInstall install = m.getInstall();
            if (install != null) {
                try {
                    install.closing();
                    install.close();
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "close() of " + m + " failed", e);
                }
            }
            ModuleClassLoader loader = loaders.remove(m);
            systemLoader.removeModule(loader);
            enabledOrder.remove(m);
            m.setEnabled(false, null, null);
            closeQuietly(loader);
        }
        if (!order.isEmpty()) {
            List<ModuleInfo> snapshot = Collections.unmodifiableList(order);
            for (ModuleListener l : listeners) {
                l.modulesDisabled(snapshot);
            }
        }
    }

    @Override
    public synchronized boolean closing() {
        for (int i = enabledOrder.size() - 1; i >= 0; i--) {
            ModuleInstall install = enabledOrder.get(i).getInstall();
            if (install != null && !install.closing()) {
                LOG.log(Level.INFO, "Shutdown vetoed by {0}", enabledOrder.get(i));
                return false;
            }
        }
        return true;
    }

    @Override
    public synchronized void close() {
        for (int i = enabledOrder.size() - 1; i >= 0; i--) {
            ModuleInstall install = enabledOrder.get(i).getInstall();
            if (install != null) {
                try {
                    install.close();
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "close() of " + enabledOrder.get(i) + " failed", e);
                }
            }
        }
    }

    @Override
    public void addModuleListener(ModuleListener l) {
        listeners.add(l);
    }

    @Override
    public void removeModuleListener(ModuleListener l) {
        listeners.remove(l);
    }

    private static void closeQuietly(ModuleClassLoader loader) {
        if (loader == null) {
            return;
        }
        try {
            loader.close();
        } catch (IOException e) {
            // ignore
        }
    }
}
