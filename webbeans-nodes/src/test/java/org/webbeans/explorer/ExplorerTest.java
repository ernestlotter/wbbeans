package org.webbeans.explorer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.io.IOException;
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
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.webbeans.actions.AbstractAction;
import org.webbeans.actions.Action;
import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionMap;
import org.webbeans.actions.MenuItem;
import org.webbeans.actions.Popup;
import org.webbeans.auth.Authentication;
import org.webbeans.auth.Principal;
import org.webbeans.core.json.Json;
import org.webbeans.core.lookup.WbDefaultLookup;
import org.webbeans.dialogs.DialogComponent;
import org.webbeans.nodes.AbstractNode;
import org.webbeans.nodes.Children;
import org.webbeans.nodes.Node;
import org.webbeans.nodes.PasteType;
import org.webbeans.nodes.PropertySupport;
import org.webbeans.nodes.Sheet;
import org.webbeans.nodes.Transferable;
import org.webbeans.nodes.Transferables;
import org.webbeans.ui.Component;
import org.webbeans.ui.Transport;
import org.webbeans.ui.UI;
import org.webbeans.ui.UIProvider;
import org.webbeans.ui.UISession;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;

/** Explorer views on a headless UI: payloads, selection ↔ lookup, events from the client. */
public class ExplorerTest {

    private static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    };

    /** A window-like container that owns the manager. */
    static final class Owner extends Panel implements ExplorerManager.Provider {
        final ExplorerManager em = new ExplorerManager();

        Owner() {
            super(new Layout.Border());
        }

        @Override
        public ExplorerManager getExplorerManager() {
            return em;
        }

        void lookup(Lookup l) {
            associateLookup(l);
        }
    }

    static final class Folder extends AbstractNode {
        final Children.Array kids = new Children.Array();
        final List<String> pasted = new ArrayList<String>();

        Folder(String name) {
            super(null);
            setChildren(kids);
            setName(name);
            setCanRename(true);
        }

        @Override
        public PasteType[] getPasteTypes(final Transferable t) {
            if (Transferables.nodesOf(t).isEmpty()) {
                return new PasteType[0];
            }
            return new PasteType[] {new PasteType() {
                @Override
                public Transferable paste() {
                    for (Node n : Transferables.nodesOf(t)) {
                        pasted.add(n.getName());
                    }
                    return null;
                }
            }};
        }
    }

    static final class Leaf extends AbstractNode {
        int size;

        Leaf(String name, int size) {
            super(Children.LEAF, Lookups.singleton(name));
            this.size = size;
            setName(name);
            setCanRename(true);
            setCanDestroy(true);
            setCanCut(true);
        }

        void setSizeExternally(int v) {
            size = v;
            firePropertyChange("size", null, v);
        }

        @Override
        protected Sheet createSheet() {
            Sheet s = new Sheet();
            Sheet.Set set = Sheet.createPropertiesSet();
            set.put(new PropertySupport.ReadWrite<Integer>("size", Integer.class, "Size", null) {
                @Override
                public Integer getValue() {
                    return size;
                }

                @Override
                public void setValue(Integer v) {
                    if (v < 0) {
                        throw new IllegalArgumentException("size must be positive");
                    }
                    size = v;
                    firePropertyChange("size", null, v);
                }
            });
            s.put(set);
            return s;
        }
    }

    private UISession session;
    private UI ui;
    private final List<String> sent = new ArrayList<String>();
    private Owner owner;
    private OutlineView view;
    private Folder root;
    private Folder docs;
    private Leaf a;
    private Leaf b;

    @Before
    public void setUp() {
        root = new Folder("root");
        docs = new Folder("docs");
        a = new Leaf("a.txt", 10);
        b = new Leaf("b.txt", 20);
        root.kids.add(docs, a);
        docs.kids.add(b);
        UIProvider provider = new UIProvider() {
            @Override
            public void init(UI ui) {
                owner = new Owner();
                owner.em.setRootContext(root);
                ActionMap map = new ActionMap();
                map.put(ActionMap.DELETE, ExplorerUtils.actionDelete(owner.em, false));
                owner.lookup(ExplorerUtils.createLookup(owner.em, map));
                view = new OutlineView("Name");
                view.setPropertyColumns("size", "Size");
                view.setDropTarget(true);
                owner.add(view, Layout.Border.CENTER);
                ui.setContent(owner);
            }
        };
        WbDefaultLookup.getInstance().setContent(Lookups.fixed(provider));
        Authentication auth = new Authentication(Principal.builder("alice").roles("user").build(), Instant.now(), null,
                Collections.singleton("password"), Collections.<String, Object>emptyMap());
        session = new UISession(auth, Locale.ENGLISH, null, DIRECT);
        ui = session.createUI("u1");
        final Transport t = new Transport() {
            @Override
            public void send(String json) {
                sent.add(json);
            }

            @Override
            public void close() {
            }
        };
        ui.access(new Runnable() {
            @Override
            public void run() {
                ui.connect(t, -1);
            }
        });
    }

    @After
    public void tearDown() {
        session.close();
        WbDefaultLookup.getInstance().setContent(Lookups.metaInfServices(getClass().getClassLoader()));
    }

    private void run(Runnable r) {
        ui.access(r);
    }

    private void fire(final String type, final Map<String, Object> data) {
        run(new Runnable() {
            @Override
            public void run() {
                ui.handleEvents(Collections.<Object>singletonList(map("id", view.getId(), "type", type, "data", data)));
            }
        });
    }

    private DialogComponent findDialog() {
        for (Component c : ui.getRoot().getChildren()) {
            if (c instanceof DialogComponent) {
                return (DialogComponent) c;
            }
        }
        return null;
    }

    /** Clicks the dialog button with the given test id. */
    private void answerDialog(String buttonTestId) {
        DialogComponent d = findDialog();
        assertNotNull("a dialog is open", d);
        for (final Component c : d.getChildren()) {
            if (buttonTestId.equals(c.get(Component.ID_FOR_TEST))) {
                run(new Runnable() {
                    @Override
                    public void run() {
                        ui.handleEvents(Collections.<Object>singletonList(map("id", c.getId(), "type", "click", "data", map())));
                    }
                });
                return;
            }
        }
        throw new AssertionError("No button " + buttonTestId);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** All {@code nodes} call payloads sent so far, in order. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> nodeCalls(String method) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (String msg : sent) {
            for (Object o : Json.array(Json.parseObject(msg), "ops")) {
                Map<String, Object> op = (Map<String, Object>) o;
                if ("call".equals(op.get("op")) && method.equals(op.get("m")) && ((Number) op.get("id")).intValue() == view.getId()) {
                    List<Object> args = (List<Object>) op.get("args");
                    out.add(args.isEmpty() ? new LinkedHashMap<String, Object>() : args.get(0) instanceof Map ? (Map<String, Object>) args.get(0) : map("value", args.get(0)));
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rowOf(String name) {
        for (Map<String, Object> p : nodeCalls("nodes")) {
            List<Object> add = Json.array(p, "add");
            if (add != null) {
                for (Object o : add) {
                    Map<String, Object> r = (Map<String, Object>) o;
                    if (name.equals(r.get("name"))) {
                        return r;
                    }
                }
            }
        }
        return null;
    }

    private int idOf(String name) {
        Map<String, Object> r = rowOf(name);
        assertNotNull("row for " + name, r);
        return ((Number) r.get("n")).intValue();
    }

    @Test
    public void initialPayloadHasRootAndItsChildrenWithColumns() {
        Map<String, Object> rootRow = rowOf("root");
        assertNotNull(rootRow);
        assertFalse((Boolean) rootRow.get("leaf"));
        Map<String, Object> aRow = rowOf("a.txt");
        assertEquals(idOf("root"), ((Number) aRow.get("p")).intValue());
        assertEquals("10", Json.string(Json.object(Json.object(aRow, "cols"), "size"), "text"));
        assertEquals("NUMBER", Json.string(Json.object(Json.object(aRow, "cols"), "size"), "edit"));
        assertTrue((Boolean) Json.object(aRow, "can").get("rename"));
        assertNull("children of collapsed folders are not sent", rowOf("b.txt"));
        assertFalse(nodeCalls("expand").isEmpty());
    }

    @Test
    public void expandSendsChildrenOnceAndNodeEventsAreMirrored() {
        fire("expand", map("n", idOf("docs")));
        assertNotNull(rowOf("b.txt"));
        int before = nodeCalls("nodes").size();
        fire("expand", map("n", idOf("docs")));
        assertEquals("second expand sends nothing", before, nodeCalls("nodes").size());
        run(new Runnable() {
            @Override
            public void run() {
                docs.kids.add(new Leaf("c.txt", 30));
                b.setDisplayName("B!");
                a.setSizeExternally(11);
            }
        });
        assertNotNull(rowOf("c.txt"));
        List<Map<String, Object>> calls = nodeCalls("nodes");
        Map<String, Object> last = calls.get(calls.size() - 1);
        assertNotNull(last.get("update"));
        String json = Json.write(last);
        assertTrue(json, json.contains("\"11\""));
        run(new Runnable() {
            @Override
            public void run() {
                docs.kids.remove(b);
            }
        });
        calls = nodeCalls("nodes");
        assertTrue(Json.write(calls.get(calls.size() - 1)).contains("\"remove\":[" + idOf("b.txt") + "]"));
    }

    @Test
    public void selectionFlowsIntoTheLookupAndBack() {
        fire("select", map("nodes", Arrays.asList(idOf("a.txt"))));
        assertEquals(1, owner.em.getSelectedNodes().length);
        assertSame(a, owner.em.getSelectedNodes()[0]);
        assertSame("the node's lookup content is in the owner's lookup", "a.txt", owner.getLookup().lookup(String.class));
        assertSame(a, owner.getLookup().lookup(Node.class));
        assertNotNull(owner.getLookup().lookup(ActionMap.class));
        // server-side selection change is pushed to the client
        int before = nodeCalls("select").size();
        run(new Runnable() {
            @Override
            public void run() {
                try {
                    owner.em.setSelectedNodes(new Node[] {docs});
                } catch (PropertyVetoException e) {
                    throw new AssertionError(e);
                }
            }
        });
        assertEquals(before + 1, nodeCalls("select").size());
        // a veto reverts the client
        owner.em.addVetoableChangeListener(new VetoableChangeListener() {
            @Override
            public void vetoableChange(PropertyChangeEvent evt) throws PropertyVetoException {
                throw new PropertyVetoException("no", evt);
            }
        });
        before = nodeCalls("select").size();
        fire("select", map("nodes", Arrays.asList(idOf("a.txt"))));
        assertSame(docs, owner.em.getSelectedNodes()[0]);
        assertEquals("revert sent", before + 1, nodeCalls("select").size());
        // unknown ids are ignored
        fire("select", map("nodes", Arrays.asList(999)));
    }

    @Test
    public void renameEditCellDeleteAndErrors() {
        int aId = idOf("a.txt");
        fire("rename", map("n", aId, "name", "renamed.txt"));
        assertEquals("renamed.txt", a.getName());
        List<Map<String, Object>> calls = nodeCalls("nodes");
        assertTrue("the rename arrives as an update", Json.write(calls.get(calls.size() - 1)).contains("\"update\""));
        fire("editCell", map("n", aId, "prop", "size", "text", "42"));
        assertEquals(42, a.size);
        fire("editCell", map("n", aId, "prop", "size", "text", "-1"));
        assertEquals(42, a.size);
        List<Map<String, Object>> errors = nodeCalls("editError");
        assertEquals(1, errors.size());
        assertEquals("size must be positive", errors.get(0).get("message"));
        fire("editCell", map("n", aId, "prop", "size", "text", "abc"));
        assertEquals(2, nodeCalls("editError").size());
        fire("delete", map("nodes", Arrays.asList(aId)));
        assertEquals("waits for the confirmation", 2, root.getChildren().getNodesCount());
        answerDialog("dialog-yes");
        assertEquals(1, root.getChildren().getNodesCount());
        assertFalse(Arrays.asList(root.getChildren().getNodes()).contains(a));
        // a non-destroyable node survives a delete request (no dialog either)
        fire("delete", map("nodes", Arrays.asList(idOf("docs"))));
        assertNull(findDialog());
        assertEquals(1, root.getChildren().getNodesCount());
    }

    @Test
    public void contextMenuListsCommonAndStandardActionsAndRunsThem() {
        final List<String> ran = new ArrayList<String>();
        Action open = new AbstractAction("Open") {
            @Override
            public void actionPerformed(ActionEvent e) {
                ran.add("open:" + e.getLookup().lookupAll(Node.class).size());
            }
        };
        a.setActions(open);
        fire("expand", map("n", idOf("docs")));
        b.setActions(open);
        fire("contextMenu", map("nodes", Arrays.asList(idOf("a.txt"), idOf("b.txt")), "x", 10, "y", 20));
        // the popup is a component in the overlay: its items are menu items, separators between groups
        Popup popup = view.getPopup();
        assertNotNull(popup);
        assertTrue(popup.isAttached());
        List<String> labels = new ArrayList<String>();
        for (Component c : popup.getChildren()) {
            labels.add(c instanceof MenuItem ? ((MenuItem) c).getText() : "-");
        }
        assertEquals(Arrays.asList("Open", "-", "Copy", "Cut", "Paste", "-", "Delete"), labels);
        assertTrue(sent.get(sent.size() - 1), sent.get(sent.size() - 1).contains("\"show\""));
        final MenuItem first = (MenuItem) popup.getChildren().get(0);
        assertTrue(first.isEnabled());
        run(new Runnable() {
            @Override
            public void run() {
                ui.handleEvents(Collections.<Object>singletonList(map("id", first.getId(), "type", "action", "data", map())));
                ui.handleEvents(Collections.<Object>singletonList(map("id", view.getPopup().getId(), "type", "close", "data", map())));
            }
        });
        assertEquals(Arrays.asList("open:2"), ran);
        assertNull(view.getPopup());
        assertFalse(popup.isAttached());
        // single node with rename
        fire("contextMenu", map("nodes", Arrays.asList(idOf("a.txt")), "x", 0, "y", 0));
        labels.clear();
        for (Component c : view.getPopup().getChildren()) {
            labels.add(c instanceof MenuItem ? ((MenuItem) c).getText() : "-");
        }
        assertTrue(labels.toString(), labels.contains("Rename"));
    }

    @Test
    public void dropPastesIntoTheTarget() throws IOException {
        fire("drop", map("target", idOf("docs"), "index", -1, "action", "move", "nodes", Arrays.asList(idOf("a.txt"))));
        assertEquals(Arrays.asList("a.txt"), docs.pasted);
        run(new Runnable() {
            @Override
            public void run() {
                view.setDropTarget(false);
            }
        });
        fire("drop", map("target", idOf("docs"), "index", -1, "action", "move", "nodes", Arrays.asList(idOf("a.txt"))));
        assertEquals(1, docs.pasted.size());
    }

    @Test
    public void listViewFollowsTheExploredContext() {
        final ListView[] list = new ListView[1];
        run(new Runnable() {
            @Override
            public void run() {
                list[0] = new ListView();
                owner.add(list[0], Layout.Border.SOUTH);
            }
        });
        String all = String.join("\n", sent);
        assertTrue(all.contains("\"wb.ListView\""));
        run(new Runnable() {
            @Override
            public void run() {
                owner.em.setExploredContext(docs);
            }
        });
        String tail = sent.get(sent.size() - 1);
        assertTrue(tail, tail.contains("\"b.txt\"") && tail.contains("\"hasParent\":true"));
    }

    @Test
    public void propertySheetShowsCommonPropertiesAndWritesToAll() {
        final PropertySheet[] sheet = new PropertySheet[1];
        run(new Runnable() {
            @Override
            public void run() {
                sheet[0] = new PropertySheet();
                owner.add(sheet[0], Layout.Border.EAST);
                try {
                    owner.em.setSelectedNodes(new Node[] {a, b});
                } catch (PropertyVetoException e) {
                    throw new AssertionError(e);
                }
            }
        });
        String tail = sent.get(sent.size() - 1);
        assertTrue(tail, tail.contains("\"count\":2") && tail.contains("\"mixed\":true") && tail.contains("\"name\":\"size\""));
        run(new Runnable() {
            @Override
            public void run() {
                ui.handleEvents(Collections.<Object>singletonList(map("id", sheet[0].getId(), "type", "edit", "data", map("prop", "size", "text", "7"))));
            }
        });
        assertEquals(7, a.size);
        assertEquals(7, b.size);
        tail = sent.get(sent.size() - 1);
        assertTrue(tail, tail.contains("\"mixed\":false"));
    }

    @Test
    public void resyncResendsTheTree() {
        sent.clear();
        run(new Runnable() {
            @Override
            public void run() {
                ui.connect(new Transport() {
                    @Override
                    public void send(String json) {
                        sent.add(json);
                    }

                    @Override
                    public void close() {
                    }
                }, 0); // a stale seq: the history cannot replay, so a full init is sent
            }
        });
        String init = sent.get(0);
        assertTrue(init.contains("\"t\":\"init\""));
        assertTrue("the init carries the rows again", init.contains("\"name\":\"a.txt\""));
        assertTrue(init.indexOf("\"op\":\"create\"") < init.indexOf("\"m\":\"nodes\""));
    }
}
