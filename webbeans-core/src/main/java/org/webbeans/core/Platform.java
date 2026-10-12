package org.webbeans.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.webbeans.core.files.DataDir;
import org.webbeans.core.lookup.RegistryLookup;
import org.webbeans.core.lookup.WbDefaultLookup;
import org.webbeans.core.modules.DefaultModuleSystem;
import org.webbeans.core.modules.ModuleInfo;
import org.webbeans.core.modules.ModuleListener;
import org.webbeans.core.modules.ModuleSystem;
import org.webbeans.core.registry.Registry;

/**
 * Bootstraps the runtime: data directory, module system, registry and the default Lookup. The launcher
 * (step 2) calls {@link #start(Config)} before starting the HTTP server.
 *
 * <p>Startup sequence: discover modules → resolve and load (class loaders, {@code validate()}) →
 * rebuild registry and default Lookup ({@code modulesLoaded}) → {@code restored()} → ready.
 *
 * <p>Modules can also come and go while the application runs: {@link #installModule} copies a jar into the
 * install directory ({@code <datadir>/modules}) and enables it (replacing a running version of the same module,
 * re-enabling its dependents), {@link #uninstallModule} is the reverse, {@link #enableModule}/{@link #disableModule}
 * switch known modules. Every change rebuilds the registry and the default Lookup; the UI layer listens to the
 * registry and updates open sessions. {@link ModuleWatcher} does the same for jars dropped into the directory.
 */
public final class Platform {

    private static final Logger LOG = Logger.getLogger(Platform.class.getName());

    /** The capability token every module may require to express "needs the WebBeans UI". */
    public static final String TOKEN_UI = "org.webbeans.ui";

    public static final class Config {
        private Path dataDir;
        private final List<File> moduleDirs = new ArrayList<File>();
        private boolean scanClasspath = true;
        private final Set<String> tokens = new HashSet<String>();
        private ClassLoader platformLoader = Platform.class.getClassLoader();

        public Config dataDir(Path dir) {
            this.dataDir = dir;
            return this;
        }

        public Config moduleDir(File dir) {
            moduleDirs.add(dir);
            return this;
        }

        public Config scanClasspath(boolean b) {
            this.scanClasspath = b;
            return this;
        }

        public Config provides(String token) {
            tokens.add(token);
            return this;
        }

        public Config platformLoader(ClassLoader loader) {
            this.platformLoader = loader;
            return this;
        }
    }

    private static volatile Platform current;

    private final DataDir dataDir;
    private final java.util.Map<Path, Long> selfWrites = new java.util.concurrent.ConcurrentHashMap<Path, Long>();
    private final DefaultModuleSystem modules;
    private final Registry registry = new Registry();
    private final ClassLoader platformLoader;

    private Platform(DataDir dataDir, DefaultModuleSystem modules, ClassLoader platformLoader) {
        this.dataDir = dataDir;
        this.modules = modules;
        this.platformLoader = platformLoader;
    }

    /** The running platform, or null. */
    public static Platform getCurrent() {
        return current;
    }

    public static synchronized Platform start(Config config) throws IOException {
        if (current != null) {
            throw new IllegalStateException("Platform already started");
        }
        Path dir = config.dataDir != null ? config.dataDir : new File("data").toPath();
        DataDir dataDir = new DataDir(dir);
        Set<String> tokens = new HashSet<String>(config.tokens);
        tokens.add(TOKEN_UI);
        final DefaultModuleSystem modules = new DefaultModuleSystem(config.platformLoader, tokens,
                dataDir.getCacheDir().resolve("modules").toFile());
        final Platform platform = new Platform(dataDir, modules, config.platformLoader);

        modules.addModuleListener(new ModuleListener.Adapter() {
            @Override
            public void modulesLoaded(List<ModuleInfo> loaded) {
                platform.refresh();
            }

            @Override
            public void modulesDisabled(List<ModuleInfo> disabled) {
                platform.refresh();
            }
        });

        if (config.scanClasspath) {
            modules.discoverClasspath();
        }
        for (File d : config.moduleDirs) {
            modules.discover(d);
        }
        modules.discover(platform.getInstallDir().toFile());   // modules installed at run time earlier
        current = platform;
        try {
            platform.refresh(); // publish platform singletons even before modules are enabled
            modules.enableAll();
        } catch (RuntimeException e) {
            current = null;
            throw e;
        }
        LOG.log(Level.INFO, "WebBeans platform started with {0} modules enabled, data in {1}",
                new Object[] {modules.getEnabledModules().size(), dataDir.getRoot()});
        return platform;
    }

    /** Rebuilds the registry from the enabled modules and re-targets {@code Lookup.getDefault()}. */
    public void refresh() {
        registry.rebuild(modules, platformLoader);
        WbDefaultLookup def = WbDefaultLookup.getInstance();
        if (def == null) {
            LOG.log(Level.WARNING, "Lookup.getDefault() is not WebBeans' ({0}); platform services are not published",
                    Lookup.getDefault().getClass().getName());
            return;
        }
        def.setContent(
                Lookups.metaInfServices(modules.getSystemClassLoader()),
                Lookups.fixed(this, modules, registry, modules.getSystemClassLoader(), dataDir),
                new RegistryLookup(registry, "Services"));
    }

    public DataDir getDataDir() {
        return dataDir;
    }

    public ModuleSystem getModuleSystem() {
        return modules;
    }

    public Registry getRegistry() {
        return registry;
    }

    // ---- modules at run time ----------------------------------------------------------------------------------------

    /** Where {@link #installModule} puts jars: {@code <datadir>/modules} (scanned at start like a {@code --modules} dir). */
    public Path getInstallDir() {
        Path dir = dataDir.getRoot().resolve("modules");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot create " + dir, e);
        }
        return dir;
    }

    /** Reads the module manifest of a jar; throws when it is not a WebBeans module. */
    public static ModuleInfo describe(Path jar) throws IOException {
        JarFile jf = new JarFile(jar.toFile());
        try {
            Manifest mf = jf.getManifest();
            ModuleInfo info = mf == null ? null : ModuleInfo.fromManifest(mf, jar.toFile());
            if (info == null) {
                throw new IOException(jar.getFileName() + " is not a WebBeans module (no WebBeans-Module manifest attribute)");
            }
            return info;
        } finally {
            jf.close();
        }
    }

    /**
     * Installs a module jar: copies it into {@link #getInstallDir()} as {@code <codeName>.jar} and enables it. A
     * running module with the same code name is disabled first (together with the modules depending on it, which
     * are re-enabled afterwards), so this also upgrades a module in place. Returns the new module descriptor;
     * {@link ModuleInfo#getProblem()} tells why it could not be enabled, if so.
     */
    public synchronized ModuleInfo installModule(Path jar) throws IOException {
        ModuleInfo incoming = describe(jar);
        Path target = getInstallDir().resolve(incoming.getCodeName() + ".jar");
        boolean inPlace = target.toAbsolutePath().normalize().equals(jar.toAbsolutePath().normalize());
        List<ModuleInfo> reenable = retire(incoming.getCodeName());
        if (!inPlace) {
            Files.copy(jar, target, StandardCopyOption.REPLACE_EXISTING);
            selfWrites.put(target.toAbsolutePath().normalize(), System.currentTimeMillis());
        }
        ModuleInfo installed = adopt(target);
        List<ModuleInfo> batch = new ArrayList<ModuleInfo>();
        batch.add(installed);
        batch.addAll(reenable);
        modules.enable(batch);
        LOG.log(Level.INFO, "Installed module {0} from {1}: {2}", new Object[] {installed, jar.getFileName(),
            installed.isEnabled() ? "enabled" : "not enabled (" + installed.getProblem() + ")"});
        return installed;
    }

    /** Installs a module from a stream (an upload), see {@link #installModule(Path)}. */
    public ModuleInfo installModule(InputStream jar, String fileName) throws IOException {
        Path tmp = Files.createTempFile("wb-module", ".jar");
        try {
            Files.copy(jar, tmp, StandardCopyOption.REPLACE_EXISTING);
            return installModule(tmp);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Disables and forgets a module installed through {@link #installModule} and deletes its jar. Modules that
     * depend on it are disabled as well (they stay known and can be enabled again when a replacement arrives).
     */
    public synchronized void uninstallModule(String codeName) throws IOException {
        ModuleInfo info = modules.find(codeName);
        if (info == null) {
            throw new IllegalArgumentException("Unknown module " + codeName);
        }
        if (info.getLocation() == null || !info.getLocation().getParentFile().getAbsoluteFile().equals(getInstallDir().toFile().getAbsoluteFile())) {
            throw new IllegalArgumentException(codeName + " was not installed at run time (" + info.getLocation() + "); remove it from its module directory");
        }
        retire(codeName);
        for (ModuleInfo m : modules.findByLocation(info.getLocation())) {
            modules.remove(m);
        }
        Files.deleteIfExists(info.getLocation().toPath());
        refresh();
        LOG.log(Level.INFO, "Uninstalled module {0}", codeName);
    }

    /** Enables a known module (and what it needs); returns it, {@link ModuleInfo#getProblem()} set when it failed. */
    public synchronized ModuleInfo enableModule(String codeName) {
        ModuleInfo info = modules.find(codeName);
        if (info == null) {
            throw new IllegalArgumentException("Unknown module " + codeName);
        }
        if (!info.isEnabled()) {
            modules.enable(Collections.singletonList(info));
        }
        return info;
    }

    /** Disables a module together with the enabled modules that depend on it; returns what was disabled. */
    public synchronized List<ModuleInfo> disableModule(String codeName) {
        ModuleInfo info = modules.find(codeName);
        if (info == null) {
            throw new IllegalArgumentException("Unknown module " + codeName);
        }
        if (!info.isEnabled()) {
            return Collections.emptyList();
        }
        List<ModuleInfo> batch = new ArrayList<ModuleInfo>();
        batch.add(info);
        batch.addAll(modules.dependents(Collections.singletonList(info)));
        modules.disable(batch);
        return batch;
    }

    /**
     * Disables every enabled module with the code name plus its dependents and forgets the versions of it found
     * in the install directory; returns the dependents (to enable again after a replacement).
     */
    private List<ModuleInfo> retire(String codeName) {
        List<ModuleInfo> dependents = new ArrayList<ModuleInfo>();
        ModuleInfo running = modules.find(codeName);
        if (running != null && running.isEnabled()) {
            dependents = modules.dependents(Collections.singletonList(running));
            List<ModuleInfo> batch = new ArrayList<ModuleInfo>();
            batch.add(running);
            batch.addAll(dependents);
            modules.disable(batch);
        }
        Path target = getInstallDir().resolve(codeName + ".jar");
        for (ModuleInfo m : modules.findByLocation(target.toFile())) {
            modules.remove(m);
        }
        return dependents;
    }

    /** Registers the jar at the given path (already in the install directory) with the module system. */
    private ModuleInfo adopt(Path jar) throws IOException {
        ModuleInfo info = describe(jar);
        modules.add(info);
        return info;
    }

    /** For the watcher: whether the platform itself wrote this jar a moment ago (its own copy, not a user's drop). */
    boolean consumeSelfWrite(Path jar) {
        Long t = selfWrites.remove(jar.toAbsolutePath().normalize());
        return t != null && System.currentTimeMillis() - t < 30000;
    }

    /** The module whose class loader loaded the class, or null for platform classes. */
    public ModuleInfo moduleOf(Class<?> type) {
        ClassLoader l = type.getClassLoader();
        while (l != null) {
            if (l instanceof org.webbeans.core.modules.ModuleClassLoader) {
                return ((org.webbeans.core.modules.ModuleClassLoader) l).getModuleInfo();
            }
            l = l.getParent();
        }
        return null;
    }

    /** Runs the module shutdown hooks; returns false if a module vetoed. */
    public synchronized boolean shutdown(boolean force) {
        if (!modules.closing() && !force) {
            return false;
        }
        modules.close();
        WbDefaultLookup def = WbDefaultLookup.getInstance();
        if (def != null) {
            def.setContent(Lookups.metaInfServices(Platform.class.getClassLoader()));
        }
        current = null;
        return true;
    }

    /** For tests: the modules enabled, in order. */
    public List<ModuleInfo> getEnabledModules() {
        return Collections.unmodifiableList(modules.getEnabledModules());
    }
}
