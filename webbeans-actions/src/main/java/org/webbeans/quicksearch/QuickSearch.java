package org.webbeans.quicksearch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.registry.Registry;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Resource;
import org.webbeans.ui.UI;

/**
 * The Quick Search box: a text field over the main window that lists what the registered
 * {@link QuickSearchProvider}s find for the typed text, grouped by category; Enter or a click runs the result.
 * Opened by {@link QuickSearchAction} (Ctrl+I); one instance per UI.
 */
@Renderer(id = "wb.QuickSearch", resource = "org/webbeans/actions/client/quicksearch.js", css = "org/webbeans/actions/client/quicksearch.css")
public final class QuickSearch extends Component {

    private static final Logger LOG = Logger.getLogger(QuickSearch.class.getName());
    public static final String PROVIDERS = "QuickSearch/Providers";
    private static final int MAX_PER_PROVIDER = 8;

    @SuppressWarnings("unchecked")
    public static final Property<List<Map<String, Object>>> RESULTS = (Property<List<Map<String, Object>>>) (Property<?>) Property.of("results", List.class, new ArrayList<Object>());
    public static final Property<String> PLACEHOLDER = Property.of("placeholder", String.class, null);
    public static final Property<String> NO_RESULTS = Property.of("noResults", String.class, null);
    public static final Property<Boolean> OPEN = Property.of("open", Boolean.class, Boolean.FALSE).clientOwned();

    static final EventType<Event> QUERY = EventType.of("query", Event::new).lazy(150);
    static final EventType<Event> RUN = EventType.of("run", Event::new);
    static final EventType<Event> CLOSE = EventType.of("close", Event::new);

    private final List<Runnable> actions = new ArrayList<Runnable>();

    public QuickSearch() {
        super("wb.QuickSearch");
        set(PLACEHOLDER, Bundles.getMessage(QuickSearch.class, "LBL_QuickSearch"));
        set(NO_RESULTS, Bundles.getMessage(QuickSearch.class, "LBL_NoResults"));
        addListener(QUERY, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                search(e.getString("text"));
            }
        });
        addListener(RUN, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                int i = e.getInt("index", -1);
                close();
                if (i >= 0 && i < actions.size()) {
                    try {
                        actions.get(i).run();
                    } catch (RuntimeException ex) {
                        LOG.log(Level.WARNING, "Quick search result failed", ex);
                    }
                }
            }
        });
        addListener(CLOSE, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                setFromClient(OPEN, Boolean.FALSE);
            }
        });
    }

    /** The box of a UI, created on first use (attached to the root's overlay slot). */
    public static QuickSearch get(UI ui) {
        QuickSearch qs = ui.getAttribute(QuickSearch.class);
        if (qs == null) {
            qs = new QuickSearch();
            ui.setAttribute(QuickSearch.class, qs);
            ui.allowWhileModal(qs);
            ui.getRoot().add(qs, "overlay");
        }
        return qs;
    }

    public static void open(UI ui) {
        get(ui).open();
    }

    public void open() {
        set(OPEN, Boolean.TRUE);
        call("open");
    }

    public void close() {
        set(OPEN, Boolean.FALSE);
    }

    void search(String text) {
        actions.clear();
        List<Map<String, Object>> groups = new ArrayList<Map<String, Object>>();
        String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            for (QuickSearchProvider p : Registry.getDefault().instances(PROVIDERS, QuickSearchProvider.class)) {
                final List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try {
                    p.search(q, new QuickSearchProvider.Response() {
                        @Override
                        public boolean add(String displayName, String detail, Resource icon, Runnable action) {
                            if (items.size() >= MAX_PER_PROVIDER) {
                                return false;
                            }
                            Map<String, Object> m = new LinkedHashMap<String, Object>();
                            m.put("i", actions.size());
                            m.put("t", displayName);
                            if (detail != null) {
                                m.put("d", detail);
                            }
                            if (icon != null) {
                                m.put("icon", icon);
                            }
                            items.add(m);
                            actions.add(action);
                            return items.size() < MAX_PER_PROVIDER;
                        }
                    });
                } catch (RuntimeException ex) {
                    LOG.log(Level.WARNING, "Quick search provider " + p + " failed", ex);
                }
                if (!items.isEmpty()) {
                    Map<String, Object> g = new LinkedHashMap<String, Object>();
                    g.put("cat", p.getCategory());
                    g.put("items", items);
                    groups.add(g);
                }
            }
        }
        set(RESULTS, groups);
    }

    /** Case-insensitive "contains", also matching initials ("ot" → "Open Toolbars"). */
    public static boolean matches(String text, String query) {
        if (text == null) {
            return false;
        }
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains(query)) {
            return true;
        }
        StringBuilder initials = new StringBuilder();
        for (String w : t.split("[\\s_.-]+")) {
            if (!w.isEmpty()) {
                initials.append(w.charAt(0));
            }
        }
        return initials.length() > 1 && initials.toString().startsWith(query);
    }

    @Override
    protected void detached() {
        actions.clear();
    }

}
