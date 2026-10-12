package org.webbeans.core.registry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;


/**
 * Collects registrations from one {@link RegistrationProvider}. Paths are {@code Folder/Sub/name}; a path with
 * no slash is an entry in the root folder.
 *
 * <pre>
 * b.instance("Actions/File/org-example-SaveAction")
 *     .supplier(SaveAction::new)
 *     .instanceOf(Action.class)
 *     .attr("displayName", "#CTL_Save").bundle("org.example.Bundle")
 *     .position(300).roles("admin");
 * b.reference("Menu/File/org-example-SaveAction", "Actions/File/org-example-SaveAction").position(300);
 * b.separator("Menu/File").position(350);
 * b.folder("Menu/File").attr("displayName", "#CTL_FileMenu").bundle("org.example.Bundle").position(100);
 * b.hide("Menu/File/org-other-OldAction");
 * </pre>
 */
public final class RegistrationBuilder {

    /** Fluent configuration of one entry or folder. */
    public final class EntryBuilder {
        final String path;
        final Entry.Kind kind;
        final boolean folder;
        final String target;
        Function<Entry, ?> factory;
        Class<?> instanceOf;
        final Map<String, Object> attrs = new LinkedHashMap<String, Object>();
        String bundle = defaultBundle;
        int position = Integer.MAX_VALUE;
        final Set<String> roles = new LinkedHashSet<String>();

        private EntryBuilder(String path, Entry.Kind kind, boolean folder, String target) {
            this.path = path;
            this.kind = kind;
            this.folder = folder;
            this.target = target;
        }

        /** The factory creating the instance; receives the entry so it can read attributes. */
        public EntryBuilder factory(Function<Entry, ?> f) {
            this.factory = f;
            return this;
        }

        /** The factory creating the instance, when it does not need the entry. */
        public EntryBuilder supplier(final Supplier<?> s) {
            this.factory = new Function<Entry, Object>() {
                @Override
                public Object apply(Entry e) {
                    return s.get();
                }
            };
            return this;
        }

        /** A fixed object as the instance. */
        public EntryBuilder value(final Object instance) {
            return supplier(new Supplier<Object>() {
                @Override
                public Object get() {
                    return instance;
                }
            });
        }

        /** The type callers may request from this entry ({@link Entry#getInstanceOf()}); used to filter without instantiating. */
        public EntryBuilder instanceOf(Class<?> type) {
            this.instanceOf = type;
            return this;
        }

        /** An attribute; a String starting with {@code #} is a bundle key, resolved with {@link #bundle}. */
        public EntryBuilder attr(String name, Object value) {
            attrs.put(name, value);
            return this;
        }

        /** Base name of the bundle resolving {@code #KEY} attributes, e.g. {@code org.example.Bundle}. */
        public EntryBuilder bundle(String baseName) {
            this.bundle = baseName;
            return this;
        }

        public EntryBuilder position(int position) {
            this.position = position;
            return this;
        }

        public EntryBuilder roles(String... roles) {
            this.roles.addAll(Arrays.asList(roles));
            return this;
        }
    }

    private final String origin;
    private final ClassLoader loader;
    private final List<EntryBuilder> entries = new ArrayList<EntryBuilder>();
    private final List<String> hidden = new ArrayList<String>();
    private String defaultBundle;
    private int separatorCounter;

    /**
     * @param origin who contributes (a provider class name, say) — named in conflict warnings; may be null
     * @param loader the loader used to find bundles, icons and classes of the entries
     */
    public RegistrationBuilder(String origin, ClassLoader loader) {
        this.origin = origin;
        this.loader = loader;
    }

    /** Sets the bundle used by entries registered afterwards that do not set their own. */
    public RegistrationBuilder defaultBundle(String baseName) {
        this.defaultBundle = baseName;
        return this;
    }

    public EntryBuilder instance(String path) {
        return add(new EntryBuilder(normalize(path), Entry.Kind.INSTANCE, false, null));
    }

    /** A reference to an instance entry elsewhere (a menu item pointing at an action). */
    public EntryBuilder reference(String path, String targetPath) {
        return add(new EntryBuilder(normalize(path), Entry.Kind.REFERENCE, false, normalize(targetPath)));
    }

    /** A separator in the given folder; the name is generated. */
    public EntryBuilder separator(String folderPath) {
        String folder = normalize(folderPath);
        String name = "separator-" + (origin != null ? origin.replaceAll("[^A-Za-z0-9]+", "-") : "platform") + "-" + (++separatorCounter);
        return add(new EntryBuilder(folder.isEmpty() ? name : folder + "/" + name, Entry.Kind.SEPARATOR, false, null));
    }

    /** A separator with an explicit name (generated code uses this so names are stable across builds). */
    public EntryBuilder separatorAt(String path) {
        return add(new EntryBuilder(normalize(path), Entry.Kind.SEPARATOR, false, null));
    }

    /** Attributes and position for a folder (a menu's display name, say). */
    public EntryBuilder folder(String path) {
        return add(new EntryBuilder(normalize(path), null, true, null));
    }

    /** Masks an entry (or whole folder) contributed by other code. */
    public void hide(String path) {
        hidden.add(normalize(path));
    }

    private EntryBuilder add(EntryBuilder b) {
        entries.add(b);
        return b;
    }

    static String normalize(String path) {
        if (path == null) {
            throw new NullPointerException("path");
        }
        String p = path.trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    String getOrigin() {
        return origin;
    }

    ClassLoader getLoader() {
        return loader;
    }

    List<EntryBuilder> getEntries() {
        return entries;
    }

    List<String> getHidden() {
        return hidden;
    }
}
