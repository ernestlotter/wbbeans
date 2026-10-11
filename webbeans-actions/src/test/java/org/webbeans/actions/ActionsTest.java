package org.webbeans.actions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.Lookups;
import org.webbeans.core.registry.Entry;
import org.webbeans.ui.Component;
import org.webbeans.ui.UI;
import org.webbeans.windows.MainWindow;

/** Action kinds, the registry bridge, menus, toolbars, shortcuts and popups on a headless main window. */
public class ActionsTest {

    static final List<String> LOG = new ArrayList<String>();

    /** An always-enabled listener (no-arg constructor). */
    public static final class Hello implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            LOG.add("hello");
        }
    }

    /** A single-context listener over Strings. */
    public static final class Shout implements ActionListener {
        private final String s;

        public Shout(String s) {
            this.s = s;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            LOG.add("shout:" + s);
        }
    }

    /** A multi-context listener. */
    public static final class Count implements ActionListener {
        private final List<String> all;

        public Count(List<String> all) {
            this.all = all;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            LOG.add("count:" + all.size());
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
        t.builder.folder("Menu/File").attr("displayName", "#CTL_File").bundle("org.webbeans.actions.TestBundle").position(100);
        t.builder.folder("Menu/Things").attr("displayName", "Things").position(200);
        t.builder.folder("Menu/Window").attr("displayName", "Window").position(900);
        t.builder.folder("Toolbars/Things").attr("displayName", "Things").position(100);
        t.builder.instance("Actions/Things/hello").factory(fromEntry).instanceOf(Action.class)
                .attr("displayName", "#CTL_Hello").bundle("org.webbeans.actions.TestBundle").attr("delegate", Hello.class.getName());
        t.builder.instance("Actions/Things/shout").factory(fromEntry).instanceOf(Action.class)
                .attr("displayName", "Shout").attr("delegate", Shout.class.getName()).attr("type", "java.lang.String").attr("selectionType", "EXACTLY_ONE")
                .attr("iconBase", "org/webbeans/actions/client/copy.svg");
        t.builder.instance("Actions/Things/count").factory(fromEntry).instanceOf(Action.class)
                .attr("displayName", "Count").attr("delegate", Count.class.getName()).attr("type", "java.lang.String").attr("selectionType", "ANY");
        t.builder.instance("Actions/Edit/copy").factory(fromEntry).instanceOf(Action.class)
                .attr("displayName", "Copy").attr("kind", "callback").attr("key", ActionMap.COPY);
        t.builder.reference("Menu/File/hello", "Actions/Things/hello").position(100);
        t.builder.separatorAt("Menu/File/sep").position(150);
        t.builder.reference("Menu/File/shout", "Actions/Things/shout").position(200);
        t.builder.reference("Menu/Things/count", "Actions/Things/count").position(100);
        t.builder.reference("Menu/Things/copy", "Actions/Edit/copy").position(200);
        t.builder.reference("Toolbars/Things/shout", "Actions/Things/shout").position(100);
        t.builder.reference("Toolbars/Things/count", "Actions/Things/count").position(200);
        t.builder.reference("Shortcuts/D-E", "Actions/Things/shout");
        t.builder.reference("Shortcuts/DS-H", "Actions/Things/hello");
        t.builder.instance("Actions/Window/Plain").value("Plain").attr("displayName", "Plain window").attr("topComponent", "Plain").attr("kind", "openTopComponent");
        t.builder.reference("Menu/Window/Plain", "Actions/Window/Plain").position(100);
        t.start();
    }

    @After
    public void tearDown() {
        t.close();
    }

    // ---- pure API ---------------------------------------------------------------------------------------------

    @Test
    public void keySyntax() {
        assertEquals("Ctrl+S", Utilities.keyToClient("D-S"));
        assertEquals("Ctrl+Shift+Z", Utilities.keyToClient("DS-Z"));
        assertEquals("Alt+F4", Utilities.keyToClient("O-F4"));
        assertEquals("Delete", Utilities.keyToClient("DELETE"));
        assertEquals("Ctrl+ArrowUp", Utilities.keyToClient("D-UP"));
        assertEquals("Ctrl+Alt+Shift+Enter", Utilities.keyToClient("DOS-ENTER"));
        assertEquals("F2", Utilities.keyToClient("F2"));
        assertNull(Utilities.keyToClient(""));
        assertEquals("Ctrl+Space", Utilities.keyToDisplay("Ctrl+ "));
    }

    @Test
    public void contextActionsFollowTheLookup() {
        final List<String> performed = new ArrayList<String>();
        ContextAwareAction single = Actions.context(String.class, true, false, new Actions.ContextPerformer<String>() {
            @Override
            public void perform(List<? extends String> context, ActionEvent e) {
                performed.add("single:" + context);
            }
        }, "Single", null);
        ContextAwareAction multi = Actions.context(String.class, false, true, new Actions.ContextPerformer<String>() {
            @Override
            public void perform(List<? extends String> context, ActionEvent e) {
                performed.add("multi:" + context);
            }
        }, "Multi", null);
        InstanceContent ic = new InstanceContent();
        Lookup lookup = new AbstractLookup(ic);
        Action s = single.createContextAwareInstance(lookup);
        Action m = multi.createContextAwareInstance(lookup);
        final List<String> events = new ArrayList<String>();
        PropertyChangeListener l = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                events.add(evt.getPropertyName() + "=" + evt.getNewValue());
            }
        };
        s.addPropertyChangeListener(l);
        assertFalse(s.isEnabled());
        assertFalse(m.isEnabled());
        ic.add("a");
        assertTrue(s.isEnabled());
        assertTrue(m.isEnabled());
        assertEquals(Arrays.asList("enabled=true"), events);
        ic.add("b");
        assertFalse("exactly one", s.isEnabled());
        assertTrue(m.isEnabled());
        s.actionPerformed(new ActionEvent(null, lookup));
        assertTrue("disabled actions do nothing", performed.isEmpty());
        m.actionPerformed(new ActionEvent(null, lookup));
        assertEquals(Arrays.asList("multi:[a, b]"), performed);
        ic.remove("a");
        ic.remove("b");
        assertFalse(s.isEnabled());
        assertTrue("surviveFocusChange keeps the last selection", m.isEnabled());
        m.actionPerformed(new ActionEvent(null, lookup));
        assertEquals("multi:[b]", performed.get(1));
        // the template evaluates the current UI's global context: none here
        assertFalse(single.isEnabled());
        single.setEnabled(false);
        ic.add("x");
        assertFalse("template disabled", s.isEnabled());
    }

    @Test
    public void callbackActionDelegatesToTheActionMapOfTheContext() {
        final List<String> performed = new ArrayList<String>();
        ContextAwareAction copy = Actions.callback(ActionMap.COPY, null, false, "Copy", null);
        InstanceContent ic = new InstanceContent();
        Lookup lookup = new AbstractLookup(ic);
        Action bound = copy.createContextAwareInstance(lookup);
        assertFalse(bound.isEnabled());
        ActionMap map = new ActionMap();
        AbstractAction target = new AbstractAction("copy impl") {
            @Override
            public void actionPerformed(ActionEvent e) {
                performed.add("copied");
            }
        };
        map.put(ActionMap.COPY, target);
        ic.add(map);
        assertTrue(bound.isEnabled());
        target.setEnabled(false);
        assertFalse(bound.isEnabled());
        target.setEnabled(true);
        bound.actionPerformed(new ActionEvent(null, lookup));
        assertEquals(Arrays.asList("copied"), performed);
        ic.remove(map);
        assertFalse(bound.isEnabled());
    }

    @Test
    public void fromEntryPicksTheKindFromTheAttributes() {
        Action hello = Actions.forID("Things", "hello");
        Action shout = Actions.forID("Things", "shout");
        Action count = Actions.forID("Things", "count");
        Action copy = Actions.forID("Edit", "copy");
        assertTrue(hello instanceof SimpleAction);
        assertTrue(shout instanceof ContextAwareAction);
        assertTrue(count instanceof ContextAwareAction);
        assertTrue(copy instanceof CallbackAction);
        assertSame("cached per entry", hello, Actions.forID("Things", "hello"));
        assertEquals("Hello", hello.getDisplayName());
        assertNotNull(shout.getIcon());
        assertNull(Actions.forID("Things", "nope"));
        hello.actionPerformed(new ActionEvent(null, Lookup.EMPTY));
        assertEquals(Arrays.asList("hello"), LOG);
        LOG.clear();
        shout.actionPerformed(new ActionEvent(null, Lookups.fixed("x", "y")));
        assertTrue("two strings: not exactly one", LOG.isEmpty());
        shout.actionPerformed(new ActionEvent(null, Lookups.singleton("x")));
        count.actionPerformed(new ActionEvent(null, Lookups.fixed("x", "y")));
        assertEquals(Arrays.asList("shout:x", "count:2"), LOG);
        assertEquals(Arrays.asList("Hello", "Shout"), names(Actions.forPath("Menu/File")));
        assertNull("separator", Actions.forPath("Menu/File").get(1));
    }

    private static List<String> names(List<Action> actions) {
        List<String> out = new ArrayList<String>();
        for (Action a : actions) {
            if (a != null) {
                out.add(a.getDisplayName());
            }
        }
        return out;
    }

    // ---- main window ------------------------------------------------------------------------------------------

    @Test
    public void menuBarIsBuiltFromTheRegistryAndFilledLazily() {
        MainWindow main = t.mainWindow();
        MenuBar bar = (MenuBar) main.getSlot(MainWindow.MENUBAR);
        assertNotNull(bar);
        List<String> tops = new ArrayList<String>();
        for (Component c : bar.getChildren()) {
            tops.add(((Menu) c).getText());
        }
        assertEquals(Arrays.asList("File", "Things", "Window"), tops);
        final Menu file = bar.findMenu("File");
        assertEquals("lazy", 0, file.getChildCount());
        t.fire(file, "open", TestUI.map());
        assertEquals(3, file.getChildCount());
        MenuItem hello = (MenuItem) file.getChildren().get(0);
        assertEquals("Hello", hello.getText());
        assertEquals("Ctrl+Shift+H", hello.get(MenuItem.ACCELERATOR));
        assertTrue(file.getChildren().get(1) instanceof Separator);
        MenuItem shout = (MenuItem) file.getChildren().get(2);
        assertEquals("Shout", shout.getText());
        assertFalse("no string in the global context", shout.isEnabled());
        t.fire(file, "open", TestUI.map());
        assertEquals("filled once", 3, file.getChildCount());
        // choose Hello
        t.fire(hello, "action", TestUI.map());
        assertEquals(Arrays.asList("hello"), LOG);
        // selection in the activated window enables Shout
        final TestUI.SelectionTC sel = t.selectionTC();
        t.run(new Runnable() {
            @Override
            public void run() {
                sel.requestActive();
                sel.content.add("mars");
            }
        });
        assertTrue(shout.isEnabled());
        t.fire(shout, "action", TestUI.map());
        assertEquals("shout:mars", LOG.get(1));
        // the Window menu holds the open-window action of the registration
        Menu window = bar.findMenu("Window");
        t.fire(window, "open", TestUI.map());
        MenuItem plain = (MenuItem) window.getChildren().get(0);
        assertEquals("Plain window", plain.getText());
        t.fire(plain, "action", TestUI.map());
        assertEquals("Plain", t.wm.getActivated().getTopComponentId());
    }

    @Test
    public void toolbarButtonsFollowTheSelectionAndCanBeHidden() {
        MainWindow main = t.mainWindow();
        ToolbarPanel panel = (ToolbarPanel) main.getSlot(MainWindow.TOOLBAR);
        assertEquals(1, panel.getChildCount());
        Toolbar things = (Toolbar) panel.getChildren().get(0);
        assertEquals("Things", things.getName());
        ToolbarButton shout = (ToolbarButton) things.getChildren().get(0);
        ToolbarButton count = (ToolbarButton) things.getChildren().get(1);
        assertFalse(shout.isEnabled());
        assertFalse(count.isEnabled());
        assertEquals("Shout (Ctrl+E)", shout.get(Component.TOOLTIP));
        final TestUI.SelectionTC sel = t.selectionTC();
        t.run(new Runnable() {
            @Override
            public void run() {
                sel.requestActive();
                sel.content.add("a");
                sel.content.add("b");
            }
        });
        assertFalse("exactly one", shout.isEnabled());
        assertTrue(count.isEnabled());
        t.fire(count, "action", TestUI.map());
        assertEquals(Arrays.asList("count:2"), LOG);
        // activating a window without strings disables both again
        t.run(new Runnable() {
            @Override
            public void run() {
                t.wm.findTopComponent("Plain").requestActive();
            }
        });
        assertFalse(count.isEnabled());
        // View > Toolbars: hiding goes through the overlay and rebuilds the panel
        ToolbarsMenuAction.setToolbarVisible(t.session, "Things", false);
        ToolbarPanel rebuilt = (ToolbarPanel) main.getSlot(MainWindow.TOOLBAR);
        assertEquals(0, rebuilt.getChildCount());
        ToolbarsMenuAction.setToolbarVisible(t.session, "Things", true);
        assertEquals(1, ((ToolbarPanel) main.getSlot(MainWindow.TOOLBAR)).getChildCount());
    }

    @Test
    public void shortcutsAreBoundOnTheUI() {
        UI.Root root = t.ui.getRoot();
        java.util.Map<String, Object> keymap = root.get(UI.Root.KEYMAP);
        assertEquals("Actions/Things/shout", keymap.get("Ctrl+E"));
        assertEquals("Actions/Things/hello", keymap.get("Ctrl+Shift+H"));
        t.fire(root, "shortcut", TestUI.map("key", "Ctrl+Shift+H", "action", "Actions/Things/hello"));
        assertEquals(Arrays.asList("hello"), LOG);
        t.fire(root, "shortcut", TestUI.map("key", "Ctrl+E", "action", "Actions/Things/shout"));
        assertEquals("disabled: nothing selected", 1, LOG.size());
        final TestUI.SelectionTC sel = t.selectionTC();
        t.run(new Runnable() {
            @Override
            public void run() {
                sel.requestActive();
                sel.content.add("venus");
            }
        });
        t.fire(root, "shortcut", TestUI.map("key", "Ctrl+E", "action", "Actions/Things/shout"));
        assertEquals("shout:venus", LOG.get(1));
    }

    @Test
    public void localeChangeRebuildsTheBars() {
        MainWindow main = t.mainWindow();
        MenuBar before = (MenuBar) main.getSlot(MainWindow.MENUBAR);
        t.run(new Runnable() {
            @Override
            public void run() {
                t.session.setLocale(Locale.GERMAN);
            }
        });
        MenuBar after = (MenuBar) main.getSlot(MainWindow.MENUBAR);
        assertTrue(before != after);
        assertEquals("Datei", ((Menu) after.getChildren().get(0)).getText());
        Menu file = after.findMenu("Datei");
        t.fire(file, "open", TestUI.map());
        assertEquals("Hallo", ((MenuItem) file.getChildren().get(0)).getText());
    }

    @Test
    public void popupShowsActionsOverAContextAndClosesItself() {
        final List<Popup> popups = new ArrayList<Popup>();
        t.run(new Runnable() {
            @Override
            public void run() {
                popups.add(Actions.actionsToPopup(Arrays.asList(Actions.forID("Things", "shout"), null, Actions.forID("Things", "hello")),
                        Lookups.singleton("io"), 10, 20));
            }
        });
        Popup p = popups.get(0);
        assertTrue(p.isAttached());
        assertSame(t.ui.getRoot(), p.getParent());
        assertEquals(3, p.getChildCount());
        MenuItem shout = (MenuItem) p.getChildren().get(0);
        assertTrue("bound to the popup context", shout.isEnabled());
        assertEquals(Arrays.asList(10, 20), toInts(t.callsOf(p, "show").get(0).get("args")));
        t.fire(shout, "action", TestUI.map());
        assertEquals(Arrays.asList("shout:io"), LOG);
        t.fire(p, "close", TestUI.map());
        assertFalse(p.isAttached());
    }

    private static List<Integer> toInts(Object list) {
        List<Integer> out = new ArrayList<Integer>();
        for (Object o : (List<?>) list) {
            out.add(((Number) o).intValue());
        }
        return out;
    }

    @Test
    public void editMenuCallbackReachesTheActiveWindowsActionMap() {
        MainWindow main = t.mainWindow();
        Menu things = ((MenuBar) main.getSlot(MainWindow.MENUBAR)).findMenu("Things");
        t.fire(things, "open", TestUI.map());
        MenuItem copy = (MenuItem) things.getChildren().get(1);
        assertEquals("Copy", copy.getText());
        assertFalse(copy.isEnabled());
        final TestUI.SelectionTC sel = t.selectionTC();
        t.run(new Runnable() {
            @Override
            public void run() {
                sel.actionMap.put(ActionMap.COPY, new AbstractAction("copy") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        LOG.add("copy:" + e.getLookup().lookup(String.class));
                    }
                });
                sel.requestActive();
                sel.content.add("thing");
            }
        });
        assertTrue(copy.isEnabled());
        t.fire(copy, "action", TestUI.map());
        assertEquals(Arrays.asList("copy:thing"), LOG);
    }
}
