package org.webbeans.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
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
import org.webbeans.ui.components.ComboBox;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;
import org.webbeans.ui.components.TextField;

public class UITest {

    private UISession session;
    private final List<String> log = new ArrayList<String>();
    private Panel root;
    private Label label;
    private TextField field;
    private Button button;

    private static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    };

    @Before
    public void setUp() {
        UIProvider provider = new UIProvider() {
            @Override
            public void init(UI ui) {
                root = new Panel(new Layout.Border());
                label = new Label("Hello");
                field = new TextField("init");
                field.setValidator(new Validator<String>() {
                    public String validate(String v) { return v.length() < 2 ? "too short" : null; }
                });
                field.addChangeListener(new Listener<ChangeEvent<String>>() {
                    public void onEvent(ChangeEvent<String> e) { log.add("change:" + e.getValue() + ":" + e.isValid()); label.setText("You typed " + e.getValue()); }
                });
                button = new Button("Go");
                button.addClickListener(new Listener<ClickEvent>() {
                    public void onEvent(ClickEvent e) { log.add("click:" + e.getButton()); button.setBusy(true); }
                });
                root.add(label, Layout.Border.NORTH);
                root.add(field, Layout.Border.CENTER);
                root.add(button, Layout.Border.SOUTH);
                ui.setContent(root);
                ui.setTitle("Test");
            }
        };
        WbDefaultLookup.getInstance().setContent(Lookups.fixed(provider));
        Authentication auth = new Authentication(Principal.builder("alice").roles("user").build(), Instant.now(), null,
                Collections.singleton("password"), Collections.<String, Object>emptyMap());
        session = new UISession(auth, Locale.ENGLISH, null, DIRECT);
    }

    @After
    public void tearDown() {
        session.close();
        WbDefaultLookup.getInstance().setContent(Lookups.metaInfServices(getClass().getClassLoader()));
    }

    private UI connect(RecordingTransport t) {
        final UI ui = session.createUI("u1");
        ui.access(new Runnable() { public void run() { ui.connect(t, -1); } });
        return ui;
    }

    @Test
    public void initSnapshotThenDeltas() {
        RecordingTransport t = new RecordingTransport();
        UI ui = connect(t);
        assertEquals(UI.State.ATTACHED, ui.getState());
        assertEquals(1, t.messages.size());
        Map<String, Object> init = t.last();
        assertEquals("init", init.get("t"));
        assertEquals(1L, init.get("seq"));
        assertEquals("u1", init.get("uiId"));
        List<Map<String, Object>> ops = t.lastOps();
        assertEquals(5, ops.size()); // root, panel, label, field, button
        assertEquals("wb.Root", ops.get(0).get("type"));
        assertEquals("Test", Json.object(ops.get(0), "props").get("title"));
        assertEquals("wb.Panel", ops.get(1).get("type"));
        assertEquals("content", ops.get(1).get("c"));
        assertEquals("border", Json.object(Json.object(ops.get(1), "props"), "layout").get("t"));
        assertEquals("north", ops.get(2).get("c"));
        assertEquals("Hello", Json.object(ops.get(2), "props").get("text"));
        assertEquals(Arrays.asList("change"), Json.array(ops.get(3), "events"));
        assertEquals(Arrays.asList("click"), Json.array(ops.get(4), "events"));

        t.clear();
        ui.access(new Runnable() { public void run() { label.setText("Changed"); label.setText("Changed again"); button.setEnabled(false); } });
        assertEquals(1, t.messages.size());
        Map<String, Object> delta = t.last();
        assertEquals("delta", delta.get("t"));
        assertEquals(2L, delta.get("seq"));
        List<Map<String, Object>> dops = t.lastOps();
        assertEquals(2, dops.size());
        assertEquals("Changed again", Json.object(dops.get(0), "props").get("text"));
        assertEquals(Boolean.FALSE, Json.object(dops.get(1), "props").get("enabled"));

        t.clear();
        ui.access(new Runnable() { public void run() { label.setText("Changed again"); } }); // no-op: no message
        assertTrue(t.messages.isEmpty());
    }

    @Test
    public void eventsDispatchAndClientOwnedValuesDoNotEcho() {
        RecordingTransport t = new RecordingTransport();
        final UI ui = connect(t);
        t.clear();
        final List<Object> events = new ArrayList<Object>();
        events.add(event(1, field.getId(), "change", "value", "hello"));
        ui.access(new Runnable() { public void run() { ui.handleEvents(events); } });
        assertEquals(Arrays.asList("change:hello:true"), log);
        assertEquals("hello", field.getValue());
        List<Map<String, Object>> ops = t.lastOps();
        // only the label text changed; the field's value is client-owned and not echoed; error cleared is a no-op
        assertEquals(1, ops.size());
        assertEquals(label.getId(), ((Number) ops.get(0).get("id")).intValue());

        // invalid value: ERROR set, event marked invalid
        t.clear();
        final List<Object> bad = new ArrayList<Object>();
        bad.add(event(2, field.getId(), "change", "value", "x"));
        ui.access(new Runnable() { public void run() { ui.handleEvents(bad); } });
        assertEquals("change:x:false", log.get(1));
        assertEquals("too short", field.getError());
        boolean errorSent = false;
        for (Map<String, Object> op : t.lastOps()) {
            Map<String, Object> p = Json.object(op, "props");
            if (p != null && "too short".equals(p.get("error"))) { errorSent = true; }
        }
        assertTrue(errorSent);

        // duplicate client sequence is ignored; unknown component ignored; unlistened type ignored
        final List<Object> dup = new ArrayList<Object>();
        dup.add(event(2, field.getId(), "change", "value", "again"));
        dup.add(event(3, 9999, "click", "button", 0));
        dup.add(event(4, label.getId(), "click", "button", 0));
        ui.access(new Runnable() { public void run() { ui.handleEvents(dup); } });
        assertEquals(2, log.size());

        final List<Object> click = new ArrayList<Object>();
        click.add(event(5, button.getId(), "click", "button", 2));
        ui.access(new Runnable() { public void run() { ui.handleEvents(click); } });
        assertEquals("click:2", log.get(2));
        assertTrue(button.get(Button.BUSY));
    }

    @Test
    public void reconnectReplaysOrResyncs() {
        RecordingTransport t = new RecordingTransport();
        final UI ui = connect(t);
        ui.access(new Runnable() { public void run() { label.setText("a"); } });
        ui.disconnect(t);
        assertEquals(UI.State.DETACHED, ui.getState());
        ui.access(new Runnable() { public void run() { label.setText("b"); } });
        ui.access(new Runnable() { public void run() { label.setText("c"); } });
        assertEquals(2, t.messages.size()); // nothing sent while detached

        final RecordingTransport t2 = new RecordingTransport();
        ui.access(new Runnable() { public void run() { ui.connect(t2, 2); } });
        assertEquals(2, t2.messages.size()); // seq 3 and 4 replayed
        assertEquals(3L, Json.parseObject(t2.messages.get(0)).get("seq"));
        assertEquals(4L, Json.parseObject(t2.messages.get(1)).get("seq"));
        assertEquals(UI.State.ATTACHED, ui.getState());

        // a client that is far behind gets a full init
        final RecordingTransport t3 = new RecordingTransport();
        for (int i = 0; i < 100; i++) {
            final int n = i;
            ui.access(new Runnable() { public void run() { label.setText("v" + n); } });
        }
        ui.access(new Runnable() { public void run() { ui.connect(t3, 2); } });
        assertEquals(1, t3.messages.size());
        assertEquals("init", t3.last().get("t"));
        assertTrue(t2.closed);
        assertEquals("v99", Json.object(t3.lastOps().get(2), "props").get("text"));
    }

    @Test
    public void addRemoveMerging() {
        RecordingTransport t = new RecordingTransport();
        final UI ui = connect(t);
        t.clear();
        ui.access(new Runnable() { public void run() {
            Label tmp = new Label("tmp"); root.add(tmp); tmp.setText("x"); root.remove(tmp);      // create + remove cancel
            Panel p = new Panel(new Layout.Flow(Layout.Orientation.HORIZONTAL, 4)); p.add(new Label("inner")); root.add(p, Layout.Border.EAST);
            p.call("scrollTo", 10);
            root.remove(label);
        } });
        List<Map<String, Object>> ops = t.lastOps();
        assertEquals(4, ops.size());
        assertEquals("create", ops.get(0).get("op")); // panel
        assertEquals("create", ops.get(1).get("op")); // inner label
        assertEquals("call", ops.get(2).get("op"));
        assertEquals(Arrays.asList(10L), Json.array(ops.get(2), "args"));
        assertEquals("remove", ops.get(3).get("op"));
        assertNull(label.getUI());
        assertEquals(0, label.getId());
    }

    @Test
    public void strictThreadingRejectsOffThreadAccess() {
        RecordingTransport t = new RecordingTransport();
        connect(t);
        try {
            label.setText("off-thread");
            fail();
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("ui.access"));
        }
        UISession.setStrictThreading(false);
        try {
            label.setText("allowed");
        } finally {
            UISession.setStrictThreading(true);
        }
    }

    @Test
    public void comboBoxTravelsAsKeys() {
        RecordingTransport t = new RecordingTransport();
        final UI ui = connect(t);
        final ComboBox<Locale> combo = new ComboBox<Locale>();
        final List<String> picked = new ArrayList<String>();
        ui.access(new Runnable() { public void run() {
            combo.setKeyFunction(l -> l.toLanguageTag()).setItems(Arrays.asList(Locale.ENGLISH, Locale.GERMAN), l -> l.getDisplayLanguage(Locale.ENGLISH));
            combo.setValue(Locale.GERMAN);
            combo.addChangeListener(e -> picked.add(e.getValue() == null ? "null" : e.getValue().toLanguageTag()));
            root.add(combo, Layout.Border.WEST);
        } });
        Map<String, Object> props = Json.object(t.lastOps().get(0), "props");
        assertEquals("de", props.get("value"));
        assertEquals("German", ((Map<?, ?>) Json.array(props, "items").get(1)).get("l"));
        final List<Object> ev = new ArrayList<Object>();
        ev.add(event(1, combo.getId(), "change", "value", "en"));
        ui.access(new Runnable() { public void run() { ui.handleEvents(ev); } });
        assertSame(Locale.ENGLISH, combo.getValue());
        assertEquals(Arrays.asList("en"), picked);
        final List<Object> badEv = new ArrayList<Object>();
        badEv.add(event(2, combo.getId(), "change", "value", "xx"));
        ui.access(new Runnable() { public void run() { ui.handleEvents(badEv); } });
        assertSame("unknown key keeps the old value", Locale.ENGLISH, combo.getValue());
        assertNotNull(combo.getError());
    }

    @Test
    public void sessionLookupAndEventBus() {
        RecordingTransport t = new RecordingTransport();
        UI ui = connect(t);
        assertSame(session, ui.getLookup().lookup(UISession.class));
        assertEquals("alice", button.getLookup().lookup(Principal.class).getId());
        assertSame(ui, button.getLookup().lookup(UI.class));
        final List<String> got = new ArrayList<String>();
        session.subscribe(String.class, s -> got.add(s));
        session.publish("hello");
        session.publish(42);
        assertEquals(Arrays.asList("hello"), got);
        assertFalse(session.isSessionThread());
    }

    private static Map<String, Object> event(long s, int id, String type, String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("s", s);
        m.put("id", id);
        m.put("type", type);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put(key, value);
        m.put("data", data);
        return m;
    }
}
