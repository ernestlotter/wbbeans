package org.webbeans.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openide.util.lookup.Lookups;
import org.webbeans.auth.Authentication;
import org.webbeans.auth.Principal;
import org.webbeans.core.json.Json;
import org.webbeans.core.lookup.WbDefaultLookup;
import org.webbeans.ui.components.Button;
import org.webbeans.ui.components.DatePicker;
import org.webbeans.ui.components.ListBox;
import org.webbeans.ui.components.NumberField;
import org.webbeans.ui.components.Panel;
import org.webbeans.ui.components.RadioGroup;
import org.webbeans.ui.components.Slider;
import org.webbeans.ui.components.SplitPane;
import org.webbeans.ui.components.TextField;
import org.webbeans.ui.components.Tree;
import org.webbeans.ui.components.TreeModel;
import org.webbeans.ui.components.Upload;
import org.webbeans.ui.components.UploadedFile;

/** The step-11 form inputs, lists and helpers: wire encoding, client events, validation, Form, Tree, downloads. */
public class ComponentsTest {

    private static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    };

    private UISession session;
    private UI ui;
    private RecordingTransport t;
    private Panel root;

    @Before
    public void setUp() {
        UIProvider provider = new UIProvider() {
            @Override
            public void init(UI ui) {
                root = new Panel();
                ui.setContent(root);
            }
        };
        WbDefaultLookup.getInstance().setContent(Lookups.fixed(provider));
        Authentication auth = new Authentication(Principal.builder("alice").roles("user").build(), Instant.now(), null,
                Collections.singleton("password"), Collections.<String, Object>emptyMap());
        session = new UISession(auth, Locale.US, null, DIRECT);
        t = new RecordingTransport();
        ui = session.createUI("u1");
        ui.access(new Runnable() {
            @Override
            public void run() {
                ui.connect(t, -1);
            }
        });
        t.clear();
    }

    @After
    public void tearDown() {
        session.close();
        WbDefaultLookup.getInstance().setContent(Lookups.metaInfServices(getClass().getClassLoader()));
    }

    private void run(Runnable r) {
        ui.access(r);
    }

    private void send(final Component c, final String type, final Map<String, Object> data) {
        Map<String, Object> ev = new LinkedHashMap<String, Object>();
        ev.put("id", c.getId());
        ev.put("type", type);
        ev.put("data", data);
        final List<Object> events = Collections.<Object>singletonList(ev);
        ui.access(new Runnable() {
            @Override
            public void run() {
                ui.handleEvents(events);
            }
        });
    }

    private static Map<String, Object> data(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** The props of the create op for a component in the last message. */
    private Map<String, Object> createProps(Component c) {
        for (Map<String, Object> op : t.lastOps()) {
            if ("create".equals(op.get("op")) && Json.integer(op, "id", -1) == c.getId()) {
                return Json.object(op, "props");
            }
        }
        return null;
    }

    private List<Map<String, Object>> opsOf(String op, Component c) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (String m : t.messages) {
            for (Object o : Json.array(Json.parseObject(m), "ops")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mo = (Map<String, Object>) o;
                if (op.equals(mo.get("op")) && Json.integer(mo, "id", -1) == c.getId()) {
                    out.add(mo);
                }
            }
        }
        return out;
    }

    @Test
    public void numberFieldFormatsForTheLocaleAndValidatesTheRange() {
        final NumberField n = new NumberField();
        final List<String> log = new ArrayList<String>();
        run(new Runnable() {
            @Override
            public void run() {
                n.setRange(0, 100).setDecimals(1).setSuffix("%");
                n.addChangeListener(new Listener<ChangeEvent<Double>>() {
                    @Override
                    public void onEvent(ChangeEvent<Double> e) {
                        log.add(e.getValue() + ":" + e.isValid());
                    }
                });
                root.add(n);
            }
        });
        Map<String, Object> props = createProps(n);
        Map<String, Object> fmt = Json.object(props, "format");
        assertEquals(".", fmt.get("decimal"));          // en-US
        assertEquals(",", fmt.get("grouping"));
        assertEquals(1L, fmt.get("maxFrac"));
        assertEquals("%", props.get("suffix"));

        send(n, "change", data("value", 42.5));
        assertEquals(Double.valueOf(42.5), n.getValue());
        assertEquals(43, n.getIntValue());
        assertEquals(Arrays.asList("42.5:true"), log);
        assertNull(n.getError());

        t.clear();
        send(n, "change", data("value", 250));
        assertEquals("Must be at most 100", n.getError().replace(" ", " "));
        assertFalse(n.isValid());
        assertEquals(Double.valueOf(250), n.getValue());   // kept, but flagged
        Map<String, Object> set = Json.object(opsOf("set", n).get(0), "props");
        assertTrue(set.containsKey("error"));

        t.clear();
        send(n, "change", data("value", "abc"));
        assertEquals("Not a number", n.getError());
        assertEquals(Double.valueOf(250), n.getValue());   // parse failures keep the previous value
        assertEquals(250.0, ((Number) Json.object(opsOf("set", n).get(0), "props").get("value")).doubleValue(), 0);

        send(n, "change", data("value", ""));
        assertNull(n.getValue());
        assertNull(n.getError());
        assertEquals("1,234.5", n.format(1234.5));
    }

    @Test
    public void requiredAndFormValidity() {
        final TextField name = new TextField();
        final NumberField age = new NumberField();
        final Button save = new Button("Save");
        final Form form = new Form();
        final Map<String, Object> model = new LinkedHashMap<String, Object>();
        run(new Runnable() {
            @Override
            public void run() {
                name.setRequired(true);
                age.setRange(0, 150);
                form.add(name, age).enable(save);
                form.bind(name, new java.util.function.Consumer<String>() {
                    @Override
                    public void accept(String v) {
                        model.put("name", v);
                    }
                });
                form.bind(age, new java.util.function.Consumer<Double>() {
                    @Override
                    public void accept(Double v) {
                        model.put("age", v);
                    }
                });
                root.add(name);
                root.add(age);
                root.add(save);
            }
        });
        assertFalse("empty required field: not valid yet, but no error shown", form.isValid());
        assertFalse(save.isEnabled());
        assertNull(name.getError());

        send(name, "change", data("value", "Ada"));
        assertTrue(form.isValid());
        assertTrue(save.isEnabled());

        send(age, "change", data("value", 200));
        assertFalse(form.isValid());
        assertFalse(save.isEnabled());
        assertEquals(age, form.firstInvalid());

        send(age, "change", data("value", 36));
        assertTrue(form.isValid());

        send(name, "change", data("value", ""));
        assertEquals("Required", name.getError());
        assertFalse(form.isValid());

        // validate() shows errors on untouched fields; write() pushes the values
        send(name, "change", data("value", "Grace"));
        run(new Runnable() {
            @Override
            public void run() {
                assertTrue(form.validate());
                form.write();
                form.reset();
            }
        });
        assertEquals("Grace", model.get("name"));
        assertEquals(Double.valueOf(36), model.get("age"));
        assertNull(age.getError());
    }

    @Test
    public void datePickerSliderAndRadioGroup() {
        final DatePicker d = new DatePicker();
        final Slider s = new Slider(0, 10, 5);
        final RadioGroup<String> r = new RadioGroup<String>(Arrays.asList("red", "green", "blue"));
        run(new Runnable() {
            @Override
            public void run() {
                d.setMin(LocalDate.of(2026, 1, 1)).setValue(LocalDate.of(2026, 10, 11));
                s.setStep(0.5).setTicks(Double.valueOf(2));
                s.addInputListener(new Listener<Slider.InputEvent>() {
                    @Override
                    public void onEvent(Slider.InputEvent e) {
                    }
                });
                r.setValue("green");
                root.add(d);
                root.add(s);
                root.add(r);
            }
        });
        assertEquals("2026-10-11", createProps(d).get("value"));
        assertEquals("2026-01-01", createProps(d).get("min"));
        assertEquals(0.5, ((Number) createProps(s).get("step")).doubleValue(), 0);
        assertEquals("green", createProps(r).get("value"));
        assertEquals(3, Json.array(createProps(r), "items").size());

        send(d, "change", data("value", "2025-12-31"));
        assertEquals(LocalDate.of(2025, 12, 31), d.getValue());
        assertNotNull(d.getError());
        send(d, "change", data("value", "2026-02-03"));
        assertNull(d.getError());
        send(d, "change", data("value", "nope"));
        assertEquals("Not a date", d.getError());
        assertEquals(LocalDate.of(2026, 2, 3), d.getValue());

        send(s, "input", data("value", 7.5));
        assertEquals(7.5, s.getDoubleValue(), 0);
        send(s, "change", data("value", 99));
        assertEquals(10.0, s.getDoubleValue(), 0);        // clamped

        send(r, "change", data("value", "blue"));
        assertEquals("blue", r.getValue());
        send(r, "change", data("value", "pink"));
        assertEquals("Unknown choice", r.getError());
        assertEquals("blue", r.getValue());
    }

    @Test
    public void listBoxMapsKeysToObjectsAndSplitPaneTracksTheDivider() {
        final ListBox<Integer> lb = new ListBox<Integer>();
        final SplitPane split = new SplitPane();
        final List<String> log = new ArrayList<String>();
        run(new Runnable() {
            @Override
            public void run() {
                lb.setMulti(true).setItems(Arrays.asList(1, 2, 3), new java.util.function.Function<Integer, String>() {
                    @Override
                    public String apply(Integer i) {
                        return "Item " + i;
                    }
                });
                lb.addSelectionListener(new Listener<ListBox.SelectionEvent>() {
                    @Override
                    public void onEvent(ListBox.SelectionEvent e) {
                        log.add("sel" + e.getSelected());
                    }
                });
                lb.addActivateListener(new Listener<ListBox.ActivateEvent>() {
                    @Override
                    public void onEvent(ListBox.ActivateEvent e) {
                        log.add("act" + e.getItem());
                    }
                });
                split.add(lb);
                split.add(new Panel());
                split.setPosition(200);
                root.add(split);
            }
        });
        assertEquals("Item 2", Json.array(createProps(lb), "items").get(1).toString().contains("Item 2") ? "Item 2" : "?");
        send(lb, "select", data("keys", Arrays.asList("3", "1")));
        assertEquals(Arrays.asList(3, 1), lb.getSelected());
        assertEquals(Integer.valueOf(3), lb.getSelectedItem());
        send(lb, "activate", data("key", "2"));
        assertEquals(Arrays.asList("sel[3, 1]", "act2"), log);
        run(new Runnable() {
            @Override
            public void run() {
                lb.setItems(Arrays.asList(1, 2));      // 3 disappears from the selection
            }
        });
        assertEquals(Arrays.asList(1), lb.getSelected());

        assertEquals(Integer.valueOf(200), split.getPosition());
        send(split, "change", data("position", 333, "collapsed", false));
        assertEquals(Integer.valueOf(333), split.getPosition());
        assertFalse(split.isCollapsed());
    }

    @Test
    public void treeFetchesChildrenLazilyAndReplaysThemOnResync() {
        final List<String> fetched = new ArrayList<String>();
        TreeModel<String> model = new TreeModel<String>() {
            @Override
            public List<String> getChildren(String parent) {
                fetched.add(String.valueOf(parent));
                if (parent == null) {
                    return Arrays.asList("a", "b");
                }
                return parent.length() < 3 ? Arrays.asList(parent + "1", parent + "2") : Collections.<String>emptyList();
            }

            @Override
            public String getLabel(String node) {
                return node.toUpperCase(Locale.ROOT);
            }

            @Override
            public boolean isLeaf(String node) {
                return node.length() >= 3;
            }
        };
        final Tree<String> tree = new Tree<String>(model);
        run(new Runnable() {
            @Override
            public void run() {
                root.add(tree);
            }
        });
        List<Object> roots = Json.array(createProps(tree), "roots");
        assertEquals(2, roots.size());
        assertEquals("A", Json.string(obj(roots.get(0)), "l"));
        assertEquals(Arrays.asList("null"), fetched);

        t.clear();
        send(tree, "expand", data("key", "a", "expanded", true));
        assertEquals(Arrays.asList("null", "a"), fetched);
        List<Map<String, Object>> calls = opsOf("call", tree);
        assertEquals(1, calls.size());
        assertEquals("children", calls.get(0).get("m"));
        assertEquals("a", Json.array(calls.get(0), "args").get(0));
        assertTrue(tree.isExpanded("a"));

        t.clear();
        send(tree, "expand", data("key", "a", "expanded", false));
        send(tree, "expand", data("key", "a", "expanded", true));
        assertEquals("children already sent are not fetched again", Arrays.asList("null", "a"), fetched);
        assertTrue(opsOf("call", tree).isEmpty());

        send(tree, "select", data("keys", Arrays.asList("a1")));
        assertEquals("a1", tree.getSelectedNode());

        // a reconnect that falls back to init: the expanded node's children are sent again
        t.clear();
        run(new Runnable() {
            @Override
            public void run() {
                ui.connect(t, -1);    // a fresh client → init
            }
        });
        assertEquals("init", t.last().get("t"));
        List<Map<String, Object>> replay = opsOf("call", tree);
        assertEquals(1, replay.size());
        assertEquals("children", replay.get(0).get("m"));
    }

    @Test
    public void uploadDeliversFilesAndDownloadServiceHandsOutOneTimeTokens() throws Exception {
        final Upload up = new Upload();
        final List<String> log = new ArrayList<String>();
        final java.nio.file.Path tmp = java.nio.file.Files.createTempFile("wb-test", ".txt");
        java.nio.file.Files.write(tmp, "hello".getBytes("UTF-8"));
        run(new Runnable() {
            @Override
            public void run() {
                up.setAccept(".txt").setMaxSize(1024L);
                up.addUploadListener(new Listener<Upload.UploadEvent>() {
                    @Override
                    public void onEvent(Upload.UploadEvent e) {
                        try {
                            log.add(e.getFile().getName() + "=" + new String(e.getFile().getBytes(), "UTF-8"));
                        } catch (java.io.IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                });
                root.add(up);
            }
        });
        assertEquals("Choose files…", createProps(up).get("text"));
        assertEquals(".txt", createProps(up).get("accept"));
        run(new Runnable() {
            @Override
            public void run() {
                up.received(new UploadedFile("notes.txt", 5, "text/plain", tmp));
            }
        });
        assertEquals(Arrays.asList("notes.txt=hello"), log);
        assertFalse("temp file deleted after the handlers", java.nio.file.Files.exists(tmp));

        t.clear();
        run(new Runnable() {
            @Override
            public void run() {
                DownloadService.offer("a,b\n1,2\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), "data.csv", "text/csv");
            }
        });
        List<Map<String, Object>> calls = opsOf("call", ui.getRoot());
        assertEquals("download", calls.get(0).get("m"));
        String url = (String) Json.array(calls.get(0), "args").get(0);
        assertTrue(url, url.startsWith("wb/download/"));
        String token = url.substring("wb/download/".length());
        DownloadService.Content c = DownloadService.take(ui, token);
        assertNotNull(c);
        assertEquals("data.csv", c.getFileName());
        assertEquals(8, c.getLength());
        assertNull("single use", DownloadService.take(ui, token));

        final org.webbeans.ui.components.Link[] link = new org.webbeans.ui.components.Link[1];
        run(new Runnable() {
            @Override
            public void run() {
                link[0] = DownloadService.link(new java.util.function.Supplier<DownloadService.Content>() {
                    @Override
                    public DownloadService.Content get() {
                        return DownloadService.content("x".getBytes(), "x.txt", "text/plain");
                    }
                }, "Get x");
                root.add(link[0]);
            }
        });
        String href = link[0].get(org.webbeans.ui.components.Link.HREF);
        String tok2 = href.substring("wb/download/".length());
        assertNotNull(DownloadService.take(ui, tok2));
        assertNotNull("reusable", DownloadService.take(ui, tok2));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> arr(Object o) {
        return (List<Object>) o;
    }
}
