package org.webbeans.actions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.webbeans.core.json.Json;
import org.webbeans.core.registry.Entry;
import org.webbeans.quicksearch.ActionsProvider;
import org.webbeans.quicksearch.QuickSearch;
import org.webbeans.quicksearch.QuickSearchProvider;
import org.webbeans.quicksearch.WindowsProvider;

/** Quick Search: providers from the registry, matching, running a result, the box on the wire. */
public class QuickSearchTest {

    static final List<String> LOG = new ArrayList<String>();

    public static final class Hello implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            LOG.add("hello");
        }
    }

    /** A provider registered by the test. */
    public static final class Fruit implements QuickSearchProvider {
        @Override
        public String getCategory() {
            return "Fruit";
        }

        @Override
        public void search(String query, Response response) {
            for (final String f : new String[] {"apple", "apricot", "banana"}) {
                if (f.startsWith(query) && !response.add(f, "a fruit", null, new Runnable() {
                    @Override
                    public void run() {
                        LOG.add("eat " + f);
                    }
                })) {
                    return;
                }
            }
        }
    }

    private TestUI t;

    @Before
    public void setUp() throws Exception {
        LOG.clear();
        t = new TestUI();
        Function<Entry, Object> fromEntry = new Function<Entry, Object>() {
            @Override
            public Object apply(Entry e) {
                return Actions.fromEntry(e);
            }
        };
        t.builder.folder("Menu/File").attr("displayName", "File").position(100);
        t.builder.folder("Menu/Window").attr("displayName", "Window").position(900);
        t.builder.instance("Actions/Things/hello").factory(fromEntry).instanceOf(Action.class).attr("displayName", "Say Hello...").attr("delegate", Hello.class.getName());
        t.builder.reference("Menu/File/hello", "Actions/Things/hello").position(100);
        t.builder.reference("Shortcuts/D-H", "Actions/Things/hello");
        t.builder.instance("Actions/Window/Plain").value("Plain").attr("displayName", "Plain window").attr("topComponent", "Plain").attr("kind", "openTopComponent");
        t.builder.reference("Menu/Window/Plain", "Actions/Window/Plain").position(100);
        t.builder.instance(QuickSearch.PROVIDERS + "/actions").value(new ActionsProvider()).instanceOf(QuickSearchProvider.class).position(100);
        t.builder.instance(QuickSearch.PROVIDERS + "/windows").value(new WindowsProvider()).instanceOf(QuickSearchProvider.class).position(200);
        t.builder.instance(QuickSearch.PROVIDERS + "/fruit").value(new Fruit()).instanceOf(QuickSearchProvider.class).position(300);
        t.start();
    }

    @After
    public void tearDown() {
        t.close();
    }

    @Test
    public void matching() {
        assertTrue(QuickSearch.matches("Open Toolbars", "tool"));
        assertTrue(QuickSearch.matches("Open Toolbars", "ot"));
        assertTrue(QuickSearch.matches("Save As", "sa"));
        assertFalse(QuickSearch.matches("Save As", "x"));
        assertFalse(QuickSearch.matches(null, "x"));
    }

    @Test
    public void providersResultsAndRun() {
        final QuickSearch[] qs = new QuickSearch[1];
        t.run(new Runnable() {
            @Override
            public void run() {
                QuickSearch.open(t.ui);
                qs[0] = QuickSearch.get(t.ui);
            }
        });
        assertNotNull(qs[0]);
        assertTrue(qs[0].isAttached());
        assertTrue(Boolean.TRUE.equals(qs[0].get(QuickSearch.OPEN)));
        t.fire(qs[0], "query", TestUI.map("text", "hel"));
        List<Map<String, Object>> groups = groups(qs[0]);
        assertEquals(1, groups.size());
        assertEquals("Actions", groups.get(0).get("cat"));
        Map<String, Object> hit = item(groups.get(0), 0);
        assertEquals("Say Hello", hit.get("t"));
        assertTrue(String.valueOf(hit.get("d")).startsWith("File › Say Hello"));
        assertTrue(String.valueOf(hit.get("d")).contains("Ctrl+H"));
        t.fire(qs[0], "run", TestUI.map("index", 0));
        assertEquals("hello", LOG.get(0));
        assertFalse(Boolean.TRUE.equals(qs[0].get(QuickSearch.OPEN)));
        // initials, several providers, the per-provider cap
        t.fire(qs[0], "query", TestUI.map("text", "ap"));
        groups = groups(qs[0]);
        assertEquals(1, groups.size());
        assertEquals("Fruit", groups.get(0).get("cat"));
        assertEquals(2, Json.array(groups.get(0), "items").size());
        assertEquals(1, Json.integer(item(groups.get(0), 1), "i", -1));
        t.fire(qs[0], "run", TestUI.map("index", 1));
        assertEquals("eat apricot", LOG.get(1));
        // open windows
        t.fire(qs[0], "query", TestUI.map("text", "plain"));
        groups = groups(qs[0]);
        boolean windows = false;
        for (Map<String, Object> g : groups) {
            windows |= "Open windows".equals(g.get("cat"));
        }
        assertTrue("the Plain window is open at startup", windows);
        // an empty query clears
        t.fire(qs[0], "query", TestUI.map("text", ""));
        assertTrue(groups(qs[0]).isEmpty());
        // a failing provider does not break the others
        t.fire(qs[0], "query", TestUI.map("text", "b"));
        assertEquals("banana", item(groups(qs[0]).get(groups(qs[0]).size() - 1), 0).get("t"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> groups(QuickSearch qs) {
        return (List<Map<String, Object>>) (List<?>) qs.get(QuickSearch.RESULTS);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> item(Map<String, Object> group, int i) {
        return (Map<String, Object>) Json.array(group, "items").get(i);
    }

}
