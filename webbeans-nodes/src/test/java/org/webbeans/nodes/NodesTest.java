package org.webbeans.nodes;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.webbeans.actions.AbstractAction;
import org.webbeans.actions.Action;
import org.webbeans.actions.ActionEvent;

/** The node model without any UI. */
public class NodesTest {

    enum Color { RED, GREEN }

    static final class Item {
        final String id;
        String label;

        Item(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Item && ((Item) o).id.equals(id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    static final class ItemChildren extends Children.Keys<Item> {
        int created;

        @Override
        protected Node[] createNodes(Item key) {
            created++;
            AbstractNode n = new AbstractNode(Children.LEAF, Lookups.singleton(key));
            n.setName(key.id);
            n.setDisplayName(key.label);
            return new Node[] {n};
        }

        void keys(Item... items) {
            setKeys(Arrays.asList(items));
        }
    }

    static final class Recorder extends NodeAdapter {
        final List<String> log = new ArrayList<String>();

        @Override
        public void childrenAdded(NodeMemberEvent ev) {
            log.add("added" + names(ev.getDelta()) + Arrays.toString(ev.getDeltaIndices()));
        }

        @Override
        public void childrenRemoved(NodeMemberEvent ev) {
            log.add("removed" + names(ev.getDelta()) + Arrays.toString(ev.getDeltaIndices()));
        }

        @Override
        public void childrenReordered(NodeReorderEvent ev) {
            log.add("reordered");
        }

        @Override
        public void nodeDestroyed(NodeEvent ev) {
            log.add("destroyed:" + ev.getNode().getName());
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            log.add("prop:" + evt.getPropertyName());
        }

        private static String names(List<Node> ns) {
            StringBuilder sb = new StringBuilder("[");
            for (Node n : ns) {
                sb.append(n.getName()).append(' ');
            }
            return sb.toString().trim() + "]";
        }
    }

    @Test
    public void keysDiffKeepsNodesOfUnchangedKeys() {
        ItemChildren ch = new ItemChildren();
        AbstractNode root = new AbstractNode(ch);
        Recorder rec = new Recorder();
        root.addNodeListener(rec);
        Item a = new Item("a", "A");
        Item b = new Item("b", "B");
        Item c = new Item("c", "C");
        ch.keys(a, b);
        Node[] first = root.getChildren().getNodes();
        assertEquals(2, first.length);
        assertSame(root, first[0].getParentNode());
        assertEquals(Arrays.asList("added[a b][0, 1]"), rec.log);
        rec.log.clear();
        ch.keys(c, a); // b removed, c added, a kept
        Node[] second = root.getChildren().getNodes();
        assertEquals("c", second[0].getName());
        assertSame("node of an unchanged key is reused", first[0], second[1]);
        assertEquals(3, ch.created);
        assertEquals(Arrays.asList("removed[b][1]", "added[c][0]"), rec.log);
        assertNull(first[1].getParentNode());
        rec.log.clear();
        ch.keys(a, c); // pure reorder
        assertEquals(Arrays.asList("reordered"), rec.log);
        assertEquals("a", root.getChildren().getNodeAt(0).getName());
        assertEquals(3, ch.created);
        ch.refreshKey(a);
        assertEquals(4, ch.created);
        assertEquals("a", root.getChildren().findChild("a").getName());
    }

    @Test
    public void childFactorySynchronousAndAsynchronous() throws Exception {
        final List<String> data = new ArrayList<String>(Arrays.asList("x", "y"));
        ChildFactory<String> f = new ChildFactory<String>() {
            @Override
            protected boolean createKeys(List<String> toPopulate) {
                toPopulate.addAll(data);
                return true;
            }

            @Override
            protected Node createNodeForKey(String key) {
                AbstractNode n = new AbstractNode(Children.LEAF);
                n.setName(key);
                return n;
            }
        };
        AbstractNode root = new AbstractNode(Children.create(f, false));
        assertFalse(root.getChildren().isInitialized());
        assertEquals(2, root.getChildren().getNodesCount());
        // asynchronous: a wait node first, the real nodes after the background computation
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        ChildFactory<String> slow = new ChildFactory<String>() {
            @Override
            protected boolean createKeys(List<String> toPopulate) {
                try {
                    latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                toPopulate.add("late");
                return true;
            }

            @Override
            protected Node createNodeForKey(String key) {
                AbstractNode n = new AbstractNode(Children.LEAF);
                n.setName(key);
                return n;
            }
        };
        AbstractNode asyncRoot = new AbstractNode(Children.create(slow, true));
        Recorder rec = new Recorder();
        asyncRoot.addNodeListener(rec);
        Node[] initial = asyncRoot.getChildren().getNodes();
        assertEquals(1, initial.length);
        assertEquals("wait", initial[0].getName());
        latch.countDown();
        long end = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < end && !"late".equals(asyncRoot.getChildren().getNodeAt(0) == null ? null : asyncRoot.getChildren().getNodeAt(0).getName())) {
            Thread.sleep(20);
        }
        assertEquals("late", asyncRoot.getChildren().getNodeAt(0).getName());
        assertEquals(1, asyncRoot.getChildren().getNodesCount());
        assertTrue(rec.log.toString(), rec.log.contains("removed[wait][0]"));
    }

    @Test
    public void filterNodeMirrorsTheOriginal() {
        ItemChildren ch = new ItemChildren();
        AbstractNode orig = new AbstractNode(ch);
        orig.setName("root");
        ch.keys(new Item("a", "A"));
        FilterNode filter = new FilterNode(orig);
        Recorder rec = new Recorder();
        filter.addNodeListener(rec);
        assertEquals("root", filter.getName());
        assertEquals(1, filter.getChildren().getNodesCount());
        assertEquals("a", filter.getChildren().getNodeAt(0).getName());
        assertTrue(filter.getChildren().getNodeAt(0) instanceof FilterNode);
        orig.setDisplayName("Root!");
        assertEquals("Root!", filter.getDisplayName());
        assertTrue(rec.log.contains("prop:displayName"));
        ch.keys(new Item("a", "A"), new Item("b", "B"));
        assertEquals(2, filter.getChildren().getNodesCount());
        assertEquals("b", filter.getChildren().getNodeAt(1).getName());
    }

    @Test
    public void propertiesEditorsAndSheet() throws Exception {
        final int[] value = {5};
        PropertySupport.ReadWrite<Integer> count = new PropertySupport.ReadWrite<Integer>("count", Integer.class, "Count", "how many") {
            @Override
            public Integer getValue() {
                return value[0];
            }

            @Override
            public void setValue(Integer v) {
                if (v < 0) {
                    throw new IllegalArgumentException("negative");
                }
                value[0] = v;
            }
        };
        assertEquals("5", count.getValueAsText());
        assertEquals(PropertyEditor.Kind.NUMBER, count.getPropertyEditor().getKind());
        count.setValue(count.getPropertyEditor().fromText("12"));
        assertEquals(12, value[0]);
        try {
            count.getPropertyEditor().fromText("abc");
            assertTrue(false);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("abc"));
        }
        PropertyEditor<Color> e = PropertyEditors.forType(Color.class);
        assertEquals(PropertyEditor.Kind.CHOICE, e.getKind());
        assertEquals(2, e.getChoices().size());
        assertEquals(Color.GREEN, e.fromText("GREEN"));
        assertEquals(PropertyEditor.Kind.BOOLEAN, PropertyEditors.forType(Boolean.class).getKind());

        Item bean = new Item("i", "Label");
        PropertySupport.Reflection<String> label = new PropertySupport.Reflection<String>(bean, String.class, null, null) {
            @Override
            public String getValue() {
                return bean.label;
            }

            @Override
            public boolean canRead() {
                return true;
            }
        };
        label.setName("label");
        assertEquals("Label", label.getValueAsText());
        assertFalse(label.canWrite());

        AbstractNode n = new AbstractNode(Children.LEAF) {
            @Override
            protected Sheet createSheet() {
                Sheet s = new Sheet();
                Sheet.Set set = Sheet.createPropertiesSet();
                set.put(count);
                s.put(set);
                return s;
            }
        };
        n.setName("n");
        assertEquals(1, n.getPropertySets().length);
        assertSame(count, n.getProperty("count"));
        assertNull(n.getProperty("nope"));
        PropertySupport.Name name = new PropertySupport.Name(n, "Name", "");
        assertFalse(name.canWrite());
        n.setCanRename(true);
        assertTrue(name.canWrite());
        name.setValue("renamed");
        assertEquals("renamed", n.getName());
    }

    @Test
    public void commonActionsAndTransferables() throws Exception {
        final List<String> performed = new ArrayList<String>();
        Action shared = new AbstractAction("Shared") {
            @Override
            public void actionPerformed(ActionEvent e) {
                performed.add("shared:" + e.getLookup().lookupAll(Item.class).size());
            }
        };
        Action only1 = new AbstractAction("Only1") {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        };
        AbstractNode a = new AbstractNode(Children.LEAF, Lookups.singleton(new Item("a", "A")));
        a.setActions(shared, null, only1);
        AbstractNode b = new AbstractNode(Children.LEAF, Lookups.singleton(new Item("b", "B")));
        b.setActions(shared);
        assertArrayEquals(new Action[] {shared, null, only1}, NodeOp.findActions(a));
        assertArrayEquals(new Action[] {shared}, NodeOp.findActions(a, b));
        assertSame(shared, a.getPreferredAction());
        final List<String> opened = new ArrayList<String>();
        AbstractNode o = new AbstractNode(Children.LEAF, Lookups.singleton(new Openable() {
            @Override
            public void open() {
                opened.add("yes");
            }
        }));
        o.getPreferredAction().actionPerformed(new ActionEvent(null, Lookup.EMPTY));
        assertEquals(1, opened.size());

        Transferable t = Transferables.multi(a.clipboardCopy(), b.clipboardCopy(), Transferables.text("hi"));
        assertEquals(Arrays.asList(a, b), Transferables.nodesOf(t));
        assertEquals("hi", t.getData(Transferables.FLAVOR_TEXT, String.class));
        assertTrue(t.isDataFlavorSupported(Transferables.FLAVOR_NODES));
        assertEquals(0, a.getPasteTypes(t).length);
        assertNull(a.getDropType(t, Node.DropAction.MOVE, -1));
    }

    @Test
    public void destroyRemovesFromArrayChildrenAndNotifies() throws Exception {
        Children.Array arr = new Children.Array();
        AbstractNode root = new AbstractNode(arr);
        AbstractNode child = new AbstractNode(Children.LEAF);
        child.setName("c");
        child.setCanDestroy(true);
        arr.add(child);
        Recorder rec = new Recorder();
        root.addNodeListener(rec);
        child.addNodeListener(rec);
        assertSame(root, child.getParentNode());
        child.destroy();
        assertEquals(0, root.getChildren().getNodesCount());
        assertTrue(rec.log.toString(), rec.log.contains("removed[c][0]") && rec.log.contains("destroyed:c"));
        AbstractNode fixed = new AbstractNode(Children.LEAF);
        try {
            fixed.destroy();
            assertTrue(false);
        } catch (java.io.IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
