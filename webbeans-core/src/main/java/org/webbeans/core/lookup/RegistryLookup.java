package org.webbeans.core.lookup;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.openide.util.lookup.AbstractLookup;
import org.webbeans.core.registry.Entry;
import org.webbeans.core.registry.Registry;
import org.webbeans.core.registry.RegistryContext;

/**
 * A NetBeans {@code Lookup} over one registry folder — what {@code Lookups.forPath("Services/Foo")} is to
 * NetBeans layers. Instances are created lazily; entries declaring {@code instanceOf} are filtered by
 * type without being instantiated. The view is the unfiltered ({@link RegistryContext#ALL}) one: a Lookup is
 * cached per path by NetBeans and cannot vary per session — use {@link Registry#folder} for session views.
 */
public final class RegistryLookup extends AbstractLookup {

    private static final long serialVersionUID = 1L;

    private static final java.util.Map<String, RegistryLookup> CACHE = new java.util.HashMap<String, RegistryLookup>();

    /**
     * The lookup over a folder of the default registry, cached per path — the WebBeans counterpart of
     * {@code Lookups.forPath}. (NetBeans' {@code Lookups.forPath} itself keeps its {@code META-INF/namedservices}
     * semantics: its provider SPI is sealed to NetBeans' own implementations.)
     */
    public static synchronized RegistryLookup forPath(String path) {
        RegistryLookup l = CACHE.get(path);
        if (l == null) {
            l = new RegistryLookup(Registry.getDefault(), path);
            CACHE.put(path, l);
        }
        return l;
    }

    private final transient Registry registry;
    private final String path;
    private final transient InstanceContent content;

    public RegistryLookup(Registry registry, String path) {
        this(registry, path, new InstanceContent());
    }

    private RegistryLookup(final Registry registry, String path, InstanceContent content) {
        super(content);
        this.registry = registry;
        this.path = path;
        this.content = content;
        refresh();
        registry.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                refresh();
            }
        });
    }

    private void refresh() {
        final List<Pair<?>> pairs = new ArrayList<Pair<?>>();
        RegistryContext.run(RegistryContext.ALL, new Runnable() {
            @Override
            public void run() {
                for (Entry e : registry.folder(path).getEntries()) {
                    if (e.getKind() == Entry.Kind.SEPARATOR) {
                        continue;
                    }
                    Entry target = e.getTarget();
                    if (target != null) {
                        pairs.add(new EntryPair(target, e.getPath()));
                    }
                }
            }
        });
        content.setPairs(pairs);
    }

    private static final class InstanceContent extends AbstractLookup.Content {
        private static final long serialVersionUID = 1L;
    }

    /** Lazily instantiating item. */
    private static final class EntryPair extends AbstractLookup.Pair<Object> {
        private static final long serialVersionUID = 1L;
        private final transient Entry entry;
        private final String id;

        EntryPair(Entry entry, String id) {
            this.entry = entry;
            this.id = id;
        }

        @Override
        protected boolean instanceOf(Class<?> c) {
            Class<?> declared = entry.getInstanceOf();
            if (declared != null) {
                return c.isAssignableFrom(declared);
            }
            Object o = entry.getInstance(Object.class);
            return o != null && c.isInstance(o);
        }

        @Override
        protected boolean creatorOf(Object obj) {
            return obj != null && obj == entry.getInstance(Object.class);
        }

        @Override
        public Object getInstance() {
            return entry.getInstance(Object.class);
        }

        @Override
        public Class<?> getType() {
            Class<?> declared = entry.getInstanceOf();
            if (declared != null) {
                return declared;
            }
            Object o = entry.getInstance(Object.class);
            return o != null ? o.getClass() : Object.class;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getDisplayName() {
            return entry.getDisplayName();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof EntryPair && ((EntryPair) o).id.equals(id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
}
