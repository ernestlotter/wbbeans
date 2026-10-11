package org.webbeans.help;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.webbeans.core.registry.Entry;
import org.webbeans.core.registry.Registry;

/**
 * The table of contents: every {@link HelpPage} the current user may see (the registry is role-filtered on the
 * session thread), as a tree of {@link Topic}s ordered by position. Built on demand from {@code Help/Pages};
 * cheap enough to rebuild after a locale change.
 */
public final class HelpSet {

    public static final String FOLDER = "Help/Pages";

    /** One page. */
    public static final class Topic {
        private final String id;
        private final String title;
        private final String resource;
        private final String parentId;
        private final String keywords;
        private final int position;
        private Topic parent;
        private final List<Topic> children = new ArrayList<Topic>();

        Topic(String id, String title, String resource, String parentId, String keywords, int position) {
            this.id = id;
            this.title = title;
            this.resource = resource;
            this.parentId = parentId;
            this.keywords = keywords == null ? "" : keywords;
            this.position = position;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        /** The class-path resource of the page. */
        public String getResource() {
            return resource;
        }

        public String getKeywords() {
            return keywords;
        }

        public int getPosition() {
            return position;
        }

        public Topic getParent() {
            return parent;
        }

        public List<Topic> getChildren() {
            return Collections.unmodifiableList(children);
        }

        /** Root first, this topic last. */
        public List<Topic> getPath() {
            List<Topic> path = new ArrayList<Topic>();
            for (Topic t = this; t != null; t = t.parent) {
                path.add(0, t);
            }
            return path;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    private static final Comparator<Topic> ORDER = new Comparator<Topic>() {
        @Override
        public int compare(Topic a, Topic b) {
            int c = Integer.compare(a.position, b.position);
            return c != 0 ? c : a.title.compareToIgnoreCase(b.title);
        }
    };

    private final Map<String, Topic> byId = new LinkedHashMap<String, Topic>();
    private final List<Topic> roots = new ArrayList<Topic>();

    private HelpSet() {
    }

    /** The help set of the current session (call on the session thread for role filtering and localised titles). */
    public static HelpSet current() {
        HelpSet set = new HelpSet();
        org.webbeans.core.registry.Folder folder = Registry.getDefault().folder(FOLDER);
        if (folder != null && folder.exists()) {
            for (Entry e : folder.getEntries()) {
                String title = e.getDisplayName();
                set.byId.put(e.getName(), new Topic(e.getName(), title == null ? e.getName() : title, e.getString("resource"),
                        e.getString("parent"), e.getString("keywords"), e.getPosition()));
            }
        }
        for (Topic t : set.byId.values()) {
            Topic p = t.parentId == null || t.parentId.isEmpty() ? null : set.byId.get(t.parentId);
            if (p == null || p == t) {
                set.roots.add(t);
            } else {
                t.parent = p;
                p.children.add(t);
            }
        }
        Collections.sort(set.roots, ORDER);
        for (Topic t : set.byId.values()) {
            Collections.sort(t.children, ORDER);
        }
        return set;
    }

    public List<Topic> getRoots() {
        return Collections.unmodifiableList(roots);
    }

    /** Every topic, depth first in table-of-contents order. */
    public List<Topic> getAll() {
        List<Topic> out = new ArrayList<Topic>();
        for (Topic r : roots) {
            collect(r, out);
        }
        return out;
    }

    private static void collect(Topic t, List<Topic> out) {
        out.add(t);
        for (Topic c : t.children) {
            collect(c, out);
        }
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    public Topic find(String id) {
        return id == null ? null : byId.get(id);
    }

    /** The first top-level topic: what F1 shows when the active window has no help of its own. */
    public Topic getHome() {
        return roots.isEmpty() ? null : roots.get(0);
    }

    /** The topic whose resource is the given class-path path (for relative links between pages). */
    public Topic byResource(String resource) {
        for (Topic t : byId.values()) {
            if (t.resource.equals(resource)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Topics whose title, keywords or text contain every word of the query (case-insensitive), title matches
     * first; an empty query matches nothing.
     */
    public List<Topic> search(String query, Locale locale) {
        List<Topic> titleHits = new ArrayList<Topic>(), textHits = new ArrayList<Topic>();
        String[] words = query == null ? new String[0] : query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) {
            return titleHits;
        }
        for (Topic t : getAll()) {
            String head = (t.title + " " + t.keywords).toLowerCase(Locale.ROOT);
            if (containsAll(head, words)) {
                titleHits.add(t);
            } else if (containsAll(HelpContent.text(this, t, locale).toLowerCase(Locale.ROOT), words)) {
                textHits.add(t);
            }
        }
        titleHits.addAll(textHits);
        return titleHits;
    }

    private static boolean containsAll(String text, String[] words) {
        for (String w : words) {
            if (!text.contains(w)) {
                return false;
            }
        }
        return true;
    }
}
