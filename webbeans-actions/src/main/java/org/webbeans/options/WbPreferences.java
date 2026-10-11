package org.webbeans.options;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.webbeans.core.files.UserFiles;
import org.webbeans.ui.UISession;

/**
 * Per-user preferences on plain files, as NetBeans' {@code NbPreferences.forModule}: one properties file per
 * module (the class's package) under {@code users/<name>/config/preferences/}. Values are read and written on
 * any thread; a write is flushed to disk immediately.
 */
public final class WbPreferences {

    private static final Logger LOG = Logger.getLogger(WbPreferences.class.getName());

    private WbPreferences() {
    }

    /** The preferences node of the current session's user for the module owning the class; null outside a session. */
    public static Preferences forModule(Class<?> cls) {
        UISession session = UISession.current();
        return session == null ? null : forModule(session, cls);
    }

    public static Preferences forModule(UISession session, Class<?> cls) {
        Package p = cls.getPackage();
        String id = p != null ? p.getName() : cls.getName();
        return forNode(session, id);
    }

    /** The preferences node with an explicit id (a file name). */
    public static Preferences forNode(UISession session, String id) {
        Store store;
        synchronized (session) {
            store = session.getAttribute(Store.class);
            if (store == null) {
                store = new Store(session.getUserFiles());
                session.setAttribute(Store.class, store);
            }
        }
        return store.node(id);
    }

    private static final class Store {
        private final UserFiles files;
        private final Map<String, FilePreferences> nodes = new LinkedHashMap<String, FilePreferences>();

        Store(UserFiles files) {
            this.files = files;
        }

        synchronized Preferences node(String id) {
            FilePreferences n = nodes.get(id);
            if (n == null) {
                Path file = null;
                try {
                    file = files.preferencesDir().resolve(id.replaceAll("[^A-Za-z0-9_.-]", "_") + ".properties");
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Cannot create the preferences directory", e);
                }
                n = new FilePreferences(id, file);
                nodes.put(id, n);
            }
            return n;
        }
    }

    /** A flat node backed by one properties file. */
    static final class FilePreferences extends AbstractPreferences {
        private final Path file;
        private final Map<String, String> values = new TreeMap<String, String>();

        FilePreferences(String name, Path file) {
            super(null, "");
            this.file = file;
            load();
        }

        private void load() {
            if (file == null || !Files.exists(file)) {
                return;
            }
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Cannot read " + file, e);
            }
            for (String k : p.stringPropertyNames()) {
                values.put(k, p.getProperty(k));
            }
        }

        private void save() {
            if (file == null) {
                return;
            }
            Properties p = new Properties();
            for (Map.Entry<String, String> e : values.entrySet()) {
                p.setProperty(e.getKey(), e.getValue());
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, null);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Cannot write " + file, e);
            }
        }

        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
            save();
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            values.remove(key);
            save();
        }

        @Override
        protected void removeNodeSpi() throws BackingStoreException {
            values.clear();
            save();
        }

        @Override
        protected String[] keysSpi() throws BackingStoreException {
            return values.keySet().toArray(new String[values.size()]);
        }

        @Override
        protected String[] childrenNamesSpi() throws BackingStoreException {
            return new String[0];
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            throw new UnsupportedOperationException("Flat preferences: no child nodes");
        }

        @Override
        protected void syncSpi() throws BackingStoreException {
            save();
        }

        @Override
        protected void flushSpi() throws BackingStoreException {
            save();
        }
    }
}
