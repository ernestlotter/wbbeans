package org.webbeans.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.webbeans.core.files.DataDir;
import org.webbeans.core.lookup.RegistryLookup;
import org.webbeans.core.lookup.WbDefaultLookup;
import org.webbeans.core.registry.Registry;

/**
 * Bootstraps the runtime: data directory, registry and the default Lookup. Everything on the class path takes
 * part — a jar contributes through its {@code META-INF/services} files ({@code RegistrationProvider}s generated
 * from the annotations, {@code @ServiceProvider} services, {@link Installer}s); there is nothing to declare, no
 * manifest and no isolation between jars. The launcher calls {@link #start(Config)} before starting the HTTP
 * server.
 *
 * <p>Start-up sequence: build the registry from the class loader → publish the platform singletons and the
 * class path's services in {@code Lookup.getDefault()} → {@link Installer#restored()} on every installer, in
 * Lookup order → ready. Shutdown runs {@link Installer#closing()} (any veto stops it unless forced) and
 * {@link Installer#close()} in reverse order.
 */
public final class Platform {

    private static final Logger LOG = Logger.getLogger(Platform.class.getName());

    public static final class Config {
        private Path dataDir;
        private ClassLoader classLoader = Platform.class.getClassLoader();

        public Config dataDir(Path dir) {
            this.dataDir = dir;
            return this;
        }

        /** The loader whose class path is the application (default: the platform's own). */
        public Config classLoader(ClassLoader loader) {
            this.classLoader = loader;
            return this;
        }
    }

    private static volatile Platform current;

    private final DataDir dataDir;
    private final Registry registry = new Registry();
    private final ClassLoader classLoader;
    private final List<Installer> installers = new ArrayList<Installer>();

    private Platform(DataDir dataDir, ClassLoader classLoader) {
        this.dataDir = dataDir;
        this.classLoader = classLoader;
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
        Platform platform = new Platform(dataDir, config.classLoader);
        current = platform;
        try {
            platform.refresh();
            platform.installers.addAll(Lookup.getDefault().lookupAll(Installer.class));
            for (Installer i : platform.installers) {
                try {
                    i.restored();
                } catch (RuntimeException e) {
                    LOG.log(Level.SEVERE, "restored() of " + i.getClass().getName() + " failed", e);
                }
            }
        } catch (RuntimeException e) {
            current = null;
            throw e;
        }
        LOG.log(Level.INFO, "WebBeans platform started: {0} registry entries, {1} installers, data in {2}",
                new Object[] {platform.registry.paths().size(), platform.installers.size(), dataDir.getRoot()});
        return platform;
    }

    /** Rebuilds the registry from the class path and re-targets {@code Lookup.getDefault()}. */
    public void refresh() {
        registry.rebuild(classLoader);
        WbDefaultLookup def = WbDefaultLookup.getInstance();
        if (def == null) {
            LOG.log(Level.WARNING, "Lookup.getDefault() is not WebBeans' ({0}); platform services are not published",
                    Lookup.getDefault().getClass().getName());
            return;
        }
        def.setContent(
                Lookups.metaInfServices(classLoader),
                Lookups.fixed(this, registry, classLoader, dataDir),
                new RegistryLookup(registry, "Services"));
    }

    public DataDir getDataDir() {
        return dataDir;
    }

    public Registry getRegistry() {
        return registry;
    }

    /** The loader the application runs from (the class path). */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /** The installers found on the class path, in the order their {@code restored()} ran. */
    public List<Installer> getInstallers() {
        return Collections.unmodifiableList(installers);
    }

    /** Runs the shutdown hooks; returns false if an installer vetoed (and {@code force} was false). */
    public synchronized boolean shutdown(boolean force) {
        List<Installer> reverse = new ArrayList<Installer>(installers);
        Collections.reverse(reverse);
        if (!force) {
            for (Installer i : reverse) {
                if (!i.closing()) {
                    LOG.log(Level.INFO, "Shutdown vetoed by {0}", i.getClass().getName());
                    return false;
                }
            }
        }
        for (Installer i : reverse) {
            try {
                i.close();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "close() of " + i.getClass().getName() + " failed", e);
            }
        }
        WbDefaultLookup def = WbDefaultLookup.getInstance();
        if (def != null) {
            def.setContent(Lookups.metaInfServices(Platform.class.getClassLoader()));
        }
        current = null;
        return true;
    }
}
