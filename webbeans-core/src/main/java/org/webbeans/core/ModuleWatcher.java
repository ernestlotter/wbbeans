package org.webbeans.core;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.webbeans.core.modules.ModuleInfo;

/**
 * Watches the platform's install directory ({@link Platform#getInstallDir()}): a jar dropped or overwritten there
 * is installed, a jar removed is uninstalled — the file-system way of doing what the Modules window does. Changes
 * are coalesced for a short while so a jar still being copied is not picked up half-written.
 */
public final class ModuleWatcher implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ModuleWatcher.class.getName());

    private final Platform platform;
    private final WatchService watcher;
    private final Thread thread;
    private final long settleMillis;

    public ModuleWatcher(Platform platform) throws IOException {
        this(platform, 1000);
    }

    ModuleWatcher(final Platform platform, long settleMillis) throws IOException {
        this.platform = platform;
        this.settleMillis = settleMillis;
        Path dir = platform.getInstallDir();
        this.watcher = FileSystems.getDefault().newWatchService();
        dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        this.thread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "wb-module-watcher");
        thread.setDaemon(true);
        thread.start();
        LOG.log(Level.INFO, "Watching {0} for module jars", dir);
    }

    private void loop() {
        Set<Path> changed = new LinkedHashSet<Path>();
        while (true) {
            WatchKey key;
            try {
                key = changed.isEmpty() ? watcher.take() : watcher.poll(settleMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return;
            } catch (ClosedWatchServiceException e) {
                return;
            }
            if (key == null) {
                // quiet for a while: apply what accumulated
                Set<Path> batch = new LinkedHashSet<Path>(changed);
                changed.clear();
                for (Path p : batch) {
                    apply(p);
                }
                continue;
            }
            Path dir = (Path) key.watchable();
            for (WatchEvent<?> ev : key.pollEvents()) {
                if (ev.kind() == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }
                Path name = (Path) ev.context();
                if (name.toString().endsWith(".jar")) {
                    changed.add(dir.resolve(name));
                }
            }
            if (!key.reset()) {
                return;
            }
        }
    }

    private void apply(Path jar) {
        try {
            if (Files.isRegularFile(jar)) {
                if (platform.consumeSelfWrite(jar)) {
                    return;   // the platform copied it there itself (installModule from elsewhere)
                }
                ModuleInfo installed = platform.installModule(jar);
                LOG.log(Level.INFO, "Watcher: {0} {1}", new Object[] {installed,
                    installed.isEnabled() ? "enabled" : "not enabled (" + installed.getProblem() + ")"});
            } else {
                String name = jar.getFileName().toString();
                String codeName = name.substring(0, name.length() - ".jar".length());
                try {
                    platform.uninstallModule(codeName);
                    LOG.log(Level.INFO, "Watcher: {0} removed", codeName);
                } catch (IllegalArgumentException e) {
                    LOG.log(Level.FINE, "Watcher: {0} removed but not a known module ({1})", new Object[] {name, e.getMessage()});
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Watcher: cannot apply " + jar, e);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Watcher: cannot apply " + jar, e);
        }
    }

    @Override
    public void close() {
        try {
            watcher.close();
        } catch (IOException e) {
            LOG.log(Level.FINE, "Closing the watch service", e);
        }
        thread.interrupt();
    }
}
