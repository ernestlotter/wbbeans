package org.webbeans.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
