package org.webbeans.core.registry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.openide.util.Lookup;
import org.webbeans.core.modules.ModuleClassLoader;
import org.webbeans.core.modules.ModuleInfo;
import org.webbeans.core.modules.ModuleSystem;

/**
 * The path-keyed registration tree assembled from every enabled module's {@link RegistrationProvider}s —
 * the useful part of NetBeans' layer filesystem (ordering, masking, per-user overlay) without XML.
 *
 * <p>The merged tree is shared and immutable between {@link #rebuild rebuilds}; {@link #folder} returns a
 * view that applies the current {@link RegistryContext} (roles, locale, user overlay) at read time.
 */
public final class Registry {

    private static final Logger LOG = Logger.getLogger(Registry.class.getName());
    private static final String SERVICE = "META-INF/services/" + RegistrationProvider.class.getName();

    /** The process-wide registry from {@code Lookup.getDefault()}, or an empty one if none is published. */
    public static Registry getDefault() {
        Registry r = Lookup.getDefault().lookup(Registry.class);
        return r != null ? r : EMPTY;
    }

    private static final Registry EMPTY = new Registry();

    private volatile Tree tree = new Tree(Collections.<String, EntryImpl>emptyMap(), Collections.<String, FolderData>emptyMap(),
            Collections.<String>emptySet());
    private final List<ChangeListener> listeners = new CopyOnWriteArrayList<ChangeListener>();

    // ---- public API ------------------------------------------------------------------------------------------

    public Folder folder(String path) {
        return new FolderView(RegistrationBuilder.normalize(path));
    }

    /** The entry at the path if it exists and is visible to the current context, else null. */
    public Entry entry(String path) {
        String p = RegistrationBuilder.normalize(path);
        EntryImpl e = tree.entries.get(p);
        return e != null && isVisible(e, RegistryContext.current()) ? e : null;
    }

    public <T> List<T> instances(String folderPath, Class<T> type) {
        return folder(folderPath).instances(type);
    }

    /** All entry paths (unfiltered), for diagnostics. */
    public Set<String> paths() {
        return Collections.unmodifiableSet(new TreeSet<String>(tree.entries.keySet()));
    }

    public void addChangeListener(ChangeListener l) {
        listeners.add(l);
    }

    public void removeChangeListener(ChangeListener l) {
        listeners.remove(l);
    }

    // ---- building --------------------------------------------------------------------------------------------

    /**
     * Collects the providers of the platform loader and of every enabled module (in dependency order) and
     * rebuilds the tree. Called by the platform on {@code modulesLoaded}.
     */
    public void rebuild(ModuleSystem modules, ClassLoader platform) {
        List<ModuleInfo> enabled = modules.getEnabledModules();
        // service files owned by module jars are the modules' contributions, even when the platform loader
        // can see them too (modules on the class path in development)
        Set<String> moduleOwned = new HashSet<String>();
        for (ModuleInfo m : enabled) {
            if (m.getClassLoader() instanceof ModuleClassLoader) {
                try {
                    for (URL u : Collections.list(((ModuleClassLoader) m.getClassLoader()).findResources(SERVICE))) {
                        moduleOwned.add(u.toExternalForm());
                    }
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Cannot enumerate registration providers of " + m, e);
                }
            }
        }
        List<RegistrationBuilder> contributions = new ArrayList<RegistrationBuilder>();
        contributions.addAll(collect(null, platform, null, moduleOwned));
        for (ModuleInfo m : enabled) {
            ClassLoader loader = m.getClassLoader();
            contributions.addAll(collect(m, loader, loader instanceof ModuleClassLoader ? (ModuleClassLoader) loader : null,
                    Collections.<String>emptySet()));
        }
        rebuild(contributions);
    }

    /** Rebuilds from the providers visible to one class loader, without a module system (tests, embedding). */
    public void rebuild(ClassLoader loader) {
        rebuild(collect(null, loader, null, Collections.<String>emptySet()));
    }

    /** Replaces the tree with the merge of the given contributions, in order (later wins on conflicts). */
    public void rebuild(List<RegistrationBuilder> contributions) {
        Map<String, EntryImpl> entries = new LinkedHashMap<String, EntryImpl>();
        Map<String, FolderData> folders = new HashMap<String, FolderData>();
        Set<String> hidden = new HashSet<String>();
        for (RegistrationBuilder b : contributions) {
            for (RegistrationBuilder.EntryBuilder eb : b.getEntries()) {
                if (eb.folder) {
                    FolderData fd = new FolderData(eb.attrs, eb.bundle, b.getLoader(), eb.position);
                    if (folders.put(eb.path, fd) != null) {
                        LOG.log(Level.FINE, "Folder {0} attributes redefined by {1}", new Object[] {eb.path, b.getModule()});
                    }
                    continue;
                }
                EntryImpl e = new EntryImpl(this, eb.path, eb.kind, eb.target, eb.factory, eb.instanceOf, eb.attrs, eb.bundle,
                        eb.position, eb.roles, b.getModule(), b.getLoader());
                EntryImpl previous = entries.put(eb.path, e);
                if (previous != null) {
                    LOG.log(Level.WARNING, "Registry entry {0} from {1} replaces the one from {2}",
                            new Object[] {eb.path, describe(b.getModule()), describe(previous.module)});
                }
            }
            hidden.addAll(b.getHidden());
        }
        this.tree = new Tree(entries, folders, hidden);
        ChangeEvent ev = new ChangeEvent(this);
        for (ChangeListener l : listeners) {
            l.stateChanged(ev);
        }
    }

    private static String describe(ModuleInfo m) {
        return m == null ? "the platform" : m.getCodeName();
    }

    /**
     * Instantiates the {@link RegistrationProvider}s listed in the loader's <em>own</em>
     * {@code META-INF/services/org.webbeans.core.registry.RegistrationProvider} (not those of its dependencies)
     * and runs them.
     */
    static List<RegistrationBuilder> collect(ModuleInfo module, ClassLoader loader, ModuleClassLoader own, Set<String> excludeUrls) {
        List<RegistrationBuilder> result = new ArrayList<RegistrationBuilder>();
        try {
            Enumeration<URL> urls = own != null ? own.findResources(SERVICE) : loader.getResources(SERVICE);
            Set<String> seen = new LinkedHashSet<String>();
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                if (excludeUrls.contains(url.toExternalForm())) {
                    continue;
                }
                for (String className : readServiceFile(url)) {
                    if (!seen.add(className)) {
                        continue;
                    }
                    try {
                        Class<?> c = Class.forName(className, true, loader);
                        RegistrationProvider p = (RegistrationProvider) c.getDeclaredConstructor().newInstance();
                        RegistrationBuilder b = new RegistrationBuilder(module, loader);
                        p.register(b);
                        result.add(b);
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, "Cannot run registration provider " + className + " of " + describe(module), e);
                    } catch (LinkageError e) {
                        LOG.log(Level.SEVERE, "Cannot load registration provider " + className + " of " + describe(module), e);
                    }
                }
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Cannot enumerate registration providers of " + describe(module), e);
        }
        return result;
    }

    private static List<String> readServiceFile(URL url) throws IOException {
        List<String> names = new ArrayList<String>();
        BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = r.readLine()) != null) {
                int hash = line.indexOf('#');
                if (hash >= 0) {
                    line = line.substring(0, hash);
                }
                line = line.trim();
                if (!line.isEmpty()) {
                    names.add(line);
                }
            }
        } finally {
            r.close();
        }
        return names;
    }

    // ---- internal model --------------------------------------------------------------------------------------

    static final class FolderData {
        final Map<String, Object> attrs;
        final String bundle;
        final ClassLoader loader;
        final int position;

        FolderData(Map<String, Object> attrs, String bundle, ClassLoader loader, int position) {
            this.attrs = new LinkedHashMap<String, Object>(attrs);
            this.bundle = bundle;
            this.loader = loader;
            this.position = position;
        }
    }

    /** Immutable merged snapshot. */
    static final class Tree {
        final Map<String, EntryImpl> entries;
        final Map<String, FolderData> folders;
        final Set<String> hidden;
        final Map<String, List<EntryImpl>> entriesByFolder = new HashMap<String, List<EntryImpl>>();
        final Map<String, Set<String>> subfolders = new HashMap<String, Set<String>>();

        Tree(Map<String, EntryImpl> entries, Map<String, FolderData> folders, Set<String> hidden) {
            this.entries = entries;
            this.folders = folders;
            this.hidden = hidden;
            for (EntryImpl e : entries.values()) {
                list(entriesByFolder, e.folderPath).add(e);
                registerAncestors(e.folderPath);
            }
            for (String f : folders.keySet()) {
                registerAncestors(f);
            }
        }

        private void registerAncestors(String folderPath) {
            String child = folderPath;
            while (!child.isEmpty()) {
                int slash = child.lastIndexOf('/');
                String parent = slash < 0 ? "" : child.substring(0, slash);
                set(subfolders, parent).add(child);
                child = parent;
            }
        }

        boolean exists(String path) {
            return path.isEmpty() || entriesByFolder.containsKey(path) || subfolders.containsKey(path) || folders.containsKey(path);
        }

        boolean isHidden(String path) {
            if (hidden.isEmpty()) {
                return false;
            }
            String p = path;
            while (true) {
                if (hidden.contains(p)) {
                    return true;
                }
                int slash = p.lastIndexOf('/');
                if (slash < 0) {
                    return false;
                }
                p = p.substring(0, slash);
            }
        }

        private static <K, V> List<V> list(Map<K, List<V>> map, K key) {
            List<V> l = map.get(key);
            if (l == null) {
                l = new ArrayList<V>();
                map.put(key, l);
            }
            return l;
        }

        private static <K, V> Set<V> set(Map<K, Set<V>> map, K key) {
            Set<V> s = map.get(key);
            if (s == null) {
                s = new LinkedHashSet<V>();
                map.put(key, s);
            }
            return s;
        }
    }

    Tree tree() {
        return tree;
    }

    boolean isVisible(EntryImpl e, RegistryContext ctx) {
        Tree t = tree;
        if (t.isHidden(e.getPath())) {
            return false;
        }
        UserOverlay overlay = ctx.getOverlay();
        if (overlay != null && overlay.isHidden(e.getPath())) {
            return false;
        }
        Set<String> roles = ctx.getRoles();
        if (roles != null && !e.roles.isEmpty()) {
            boolean any = false;
            for (String r : e.roles) {
                if (roles.contains(r)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        if (e.getKind() == Entry.Kind.REFERENCE) {
            EntryImpl target = t.entries.get(e.target);
            return target != null && target != e && isVisible(target, ctx);
        }
        return true;
    }

    /** A folder view bound to the registry; evaluates the context on every call. */
    private final class FolderView implements Folder {
        private final String path;

        FolderView(String path) {
            this.path = path;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public String getName() {
            int slash = path.lastIndexOf('/');
            return slash < 0 ? path : path.substring(slash + 1);
        }

        @Override
        public String getDisplayName() {
            Object dn = getAttributes().get("displayName");
            return dn != null ? dn.toString() : getName();
        }

        @Override
        public Map<String, Object> getAttributes() {
            FolderData fd = tree.folders.get(path);
            if (fd == null) {
                return Collections.emptyMap();
            }
            return EntryImpl.resolve(fd.attrs, fd.bundle, fd.loader, RegistryContext.current().getLocale());
        }

        @Override
        public int getPosition() {
            UserOverlay overlay = RegistryContext.current().getOverlay();
            Integer pos = overlay != null ? overlay.getPosition(path) : null;
            if (pos != null) {
                return pos;
            }
            FolderData fd = tree.folders.get(path);
            return fd != null ? fd.position : Integer.MAX_VALUE;
        }

        @Override
        public boolean exists() {
            return tree.exists(path) && !tree.isHidden(path);
        }

        @Override
        public List<Folder> getFolders() {
            Tree t = tree;
            Set<String> subs = t.subfolders.get(path);
            if (subs == null) {
                return Collections.emptyList();
            }
            RegistryContext ctx = RegistryContext.current();
            UserOverlay overlay = ctx.getOverlay();
            List<Object[]> sortable = new ArrayList<Object[]>();
            for (String sub : subs) {
                if (t.isHidden(sub) || (overlay != null && overlay.isHidden(sub))) {
                    continue;
                }
                FolderData fd = t.folders.get(sub);
                Integer pos = overlay != null ? overlay.getPosition(sub) : null;
                int position = pos != null ? pos : fd != null ? fd.position : Integer.MAX_VALUE;
                sortable.add(new Object[] {position, sub});
            }
            sortByPositionAndName(sortable);
            List<Folder> result = new ArrayList<Folder>(sortable.size());
            for (Object[] o : sortable) {
                result.add(new FolderView((String) o[1]));
            }
            return result;
        }

        @Override
        public List<Entry> getEntries() {
            Tree t = tree;
            List<EntryImpl> all = t.entriesByFolder.get(path);
            if (all == null) {
                return Collections.emptyList();
            }
            RegistryContext ctx = RegistryContext.current();
            List<Object[]> sortable = new ArrayList<Object[]>();
            for (EntryImpl e : all) {
                if (isVisible(e, ctx)) {
                    sortable.add(new Object[] {e.getPosition(), e.getName(), e});
                }
            }
            sortByPositionAndName(sortable);
            List<Entry> result = new ArrayList<Entry>(sortable.size());
            for (Object[] o : sortable) {
                result.add((Entry) o[2]);
            }
            return result;
        }

        @Override
        public Entry getEntry(String name) {
            return entry(path.isEmpty() ? name : path + "/" + name);
        }

        @Override
        public <T> List<T> instances(Class<T> type) {
            List<T> result = new ArrayList<T>();
            for (Entry e : getEntries()) {
                if (e.getKind() == Entry.Kind.SEPARATOR) {
                    continue;
                }
                Entry target = e.getTarget();
                if (target == null) {
                    continue;
                }
                Class<?> declared = target.getInstanceOf();
                if (declared != null && !type.isAssignableFrom(declared)) {
                    continue;
                }
                T instance = target.getInstance(type);
                if (instance != null) {
                    result.add(instance);
                }
            }
            return result;
        }

        @Override
        public String toString() {
            return "Folder[" + path + "]";
        }
    }

    private static void sortByPositionAndName(List<Object[]> sortable) {
        Collections.sort(sortable, new java.util.Comparator<Object[]>() {
            @Override
            public int compare(Object[] a, Object[] b) {
                int pa = (Integer) a[0];
                int pb = (Integer) b[0];
                if (pa != pb) {
                    return pa < pb ? -1 : 1;
                }
                return ((String) a[1]).compareTo((String) b[1]);
            }
        });
    }
}
