package org.webbeans.core.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.webbeans.core.i18n.Bundles;

/** The merged, shared form of a registration. Context-dependent reads consult {@link RegistryContext#current()}. */
final class EntryImpl implements Entry {

    private static final Logger LOG = Logger.getLogger(Registry.class.getName());
    private static final Object FAILED = new Object();

    private final Registry registry;
    private final String path;
    private final String name;
    final String folderPath;
    private final Kind kind;
    final String target;
    private final Function<Entry, ?> factory;
    private final Class<?> instanceOf;
    private final Map<String, Object> rawAttrs;
    private final String bundle;
    private final int position;
    final Set<String> roles;
    final String origin;
    private final ClassLoader loader;
    private volatile Object instance;

    EntryImpl(Registry registry, String path, Kind kind, String target, Function<Entry, ?> factory, Class<?> instanceOf,
            Map<String, Object> attrs, String bundle, int position, Set<String> roles, String origin, ClassLoader loader) {
        this.registry = registry;
        this.path = path;
        int slash = path.lastIndexOf('/');
        this.name = slash < 0 ? path : path.substring(slash + 1);
        this.folderPath = slash < 0 ? "" : path.substring(0, slash);
        this.kind = kind;
        this.target = target;
        this.factory = factory;
        this.instanceOf = instanceOf;
        this.rawAttrs = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(attrs));
        this.bundle = bundle;
        this.position = position;
        this.roles = Collections.unmodifiableSet(new LinkedHashSet<String>(roles));
        this.origin = origin;
        this.loader = loader;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getFolderPath() {
        return folderPath;
    }

    @Override
    public Kind getKind() {
        return kind;
    }

    @Override
    public int getPosition() {
        UserOverlay overlay = RegistryContext.current().getOverlay();
        if (overlay != null) {
            Integer p = overlay.getPosition(path);
            if (p != null) {
                return p;
            }
        }
        return position;
    }

    /** The position as registered, ignoring the user overlay. */
    int getDeclaredPosition() {
        return position;
    }

    @Override
    public ClassLoader getClassLoader() {
        return loader;
    }

    @Override
    public Set<String> getRoles() {
        return roles;
    }

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> own = resolve(rawAttrs, bundle, loader, RegistryContext.current().getLocale());
        if (kind != Kind.REFERENCE) {
            return own;
        }
        // a reference inherits its target's attributes (display name, icon...) and may override them
        Entry t = getTarget();
        if (t == null) {
            return own;
        }
        Map<String, Object> merged = new LinkedHashMap<String, Object>(t.getAttributes());
        merged.putAll(own);
        return Collections.unmodifiableMap(merged);
    }

    static Map<String, Object> resolve(Map<String, Object> raw, String bundle, ClassLoader loader, Locale locale) {
        Map<String, Object> out = new LinkedHashMap<String, Object>(raw.size());
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String && ((String) v).startsWith("#")) {
                v = Bundles.resolve(loader, bundle, (String) v, locale);
            }
            out.put(e.getKey(), v);
        }
        return Collections.unmodifiableMap(out);
    }

    @Override
    public String getString(String attribute) {
        Object v = getAttributes().get(attribute);
        return v == null ? null : v.toString();
    }

    @Override
    public String getDisplayName() {
        String dn = getString("displayName");
        return dn != null ? dn : name;
    }

    @Override
    public Class<?> getInstanceOf() {
        return instanceOf;
    }

    @Override
    public Entry getTarget() {
        if (kind != Kind.REFERENCE) {
            return this;
        }
        EntryImpl t = registry.tree().entries.get(target);
        return t == null || t == this ? null : t.getTarget();
    }

    @Override
    public <T> T getInstance(Class<T> type) {
        if (kind == Kind.REFERENCE) {
            Entry t = getTarget();
            return t == null ? null : t.getInstance(type);
        }
        Object o = instance;
        if (o == null) {
            synchronized (this) {
                o = instance;
                if (o == null) {
                    o = create();
                    instance = o == null ? FAILED : o;
                }
            }
        }
        return o != FAILED && type.isInstance(o) ? type.cast(o) : null;
    }

    @Override
    public <T> T newInstance(Class<T> type) {
        if (kind == Kind.REFERENCE) {
            Entry t = getTarget();
            return t == null ? null : t.newInstance(type);
        }
        Object o = create();
        return type.isInstance(o) ? type.cast(o) : null;
    }

    private Object create() {
        if (kind != Kind.INSTANCE || factory == null) {
            return null;
        }
        try {
            Object o = factory.apply(this);
            if (o == null) {
                LOG.log(Level.WARNING, "Factory of registry entry {0} returned null", path);
            }
            return o;
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Cannot create instance for registry entry " + path, e);
            return null;
        } catch (LinkageError e) {
            LOG.log(Level.SEVERE, "Cannot create instance for registry entry " + path, e);
            return null;
        }
    }

    @Override
    public String toString() {
        return "Entry[" + path + (kind == Kind.REFERENCE ? " -> " + target : "") + "]";
    }
}
