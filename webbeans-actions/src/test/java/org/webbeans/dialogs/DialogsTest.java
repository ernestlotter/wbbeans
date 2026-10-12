package org.webbeans.dialogs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.TestUI;
import org.webbeans.notifications.Notification;
import org.webbeans.notifications.NotificationDisplayer;
import org.webbeans.notifications.NotificationHost;
import org.webbeans.notifications.NotificationsCell;
import org.webbeans.progress.Cancellable;
import org.webbeans.progress.ProgressHandle;
import org.webbeans.progress.ProgressHandleFactory;
import org.webbeans.progress.ProgressStatusCell;
import org.webbeans.ui.Component;
import org.webbeans.ui.UI;
import org.webbeans.ui.components.Button;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.TextField;

/** Dialogs, wizards, notifications and progress on a headless main window. */
public class DialogsTest {

    private TestUI t;

    @Before
    public void setUp() throws Exception {
        t = new TestUI();
        t.start();
    }

    @After
    public void tearDown() {
        t.close();
    }

    private DialogComponent dialog() {
        for (Component c : t.ui.getRoot().getChildren()) {
            if (c instanceof DialogComponent) {
                return (DialogComponent) c;
            }
        }
        return null;
    }

    private Button button(DialogComponent d, String testId) {
        Component c = TestUI.byTestId(d, testId);
        assertNotNull(testId, c);
        return (Button) c;
    }

    private void click(Component c) {
        t.fire(c, "click", TestUI.map("button", 0));
    }

    @Test
    public void notifyCompletesTheFutureWithTheChosenOptionAndBlocksOutsideEvents() throws Exception {
        final List<CompletableFuture<Object>> futures = new ArrayList<CompletableFuture<Object>>();
        final List<Object> seen = new ArrayList<Object>();
        t.run(new Runnable() {
            @Override
            public void run() {
                CompletableFuture<Object> f = DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Confirmation("Really?", "Confirm", NotifyDescriptor.YES_NO_CANCEL_OPTION));
                f.thenAccept(new java.util.function.Consumer<Object>() {
                    @Override
                    public void accept(Object o) {
                        seen.add(o);
                        seen.add(UI.current());
                    }
                });
                futures.add(f);
            }
        });
        DialogComponent d = dialog();
        assertNotNull(d);
        assertEquals("Confirm", d.getTitle());
        assertEquals("question", d.get(DialogComponent.MESSAGE_TYPE));
        assertSame(d, t.ui.getModal());
        assertTrue(d.getSlot(DialogComponent.CONTENT) instanceof Label);
        assertNotNull(button(d, "dialog-yes"));
        assertNotNull(button(d, "dialog-no"));
        assertNotNull(button(d, "dialog-cancel"));
        assertTrue(button(d, "dialog-yes").get(Button.DEFAULT_BUTTON));
        // an event for a component outside the modal dialog is dropped
        final Button outside = new Button("outside");
        final List<String> clicks = new ArrayList<String>();
        t.run(new Runnable() {
            @Override
            public void run() {
                outside.addClickListener(new org.webbeans.ui.Listener<org.webbeans.ui.ClickEvent>() {
                    @Override
                    public void onEvent(org.webbeans.ui.ClickEvent e) {
                        clicks.add("outside");
                    }
                });
                t.selectionTC().add(outside);
            }
        });
        click(outside);
        assertTrue(clicks.isEmpty());
        assertFalse(futures.get(0).isDone());
        click(button(d, "dialog-no"));
        assertSame(NotifyDescriptor.NO_OPTION, futures.get(0).get(1, TimeUnit.SECONDS));
        assertEquals("continuation ran on the UI's executor", Arrays.asList(NotifyDescriptor.NO_OPTION, t.ui), seen);
        assertFalse(d.isAttached());
        assertNull(t.ui.getModal());
        click(outside);
        assertEquals(Arrays.asList("outside"), clicks);
        // Escape / close button → CLOSED_OPTION
        t.run(new Runnable() {
            @Override
            public void run() {
                futures.add(DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message("Hi")));
            }
        });
        DialogComponent d2 = dialog();
        assertEquals("Information", d2.getTitle());
        t.fire(d2, "close", TestUI.map());
        assertSame(NotifyDescriptor.CLOSED_OPTION, futures.get(1).get(1, TimeUnit.SECONDS));
    }

    @Test
    public void inputLineAndValidity() throws Exception {
        final NotifyDescriptor.InputLine input = new NotifyDescriptor.InputLine("Name:", "New");
        final List<CompletableFuture<Object>> futures = new ArrayList<CompletableFuture<Object>>();
        t.run(new Runnable() {
            @Override
            public void run() {
                input.setInputText("default");
                futures.add(DialogDisplayer.getDefault().notify(input));
            }
        });
        DialogComponent d = dialog();
        TextField field = (TextField) TestUI.byTestId(d, "dialog-input");
        assertEquals("default", field.getValue());
        assertFalse("focus requested", t.callsOf(field, "focus").isEmpty());
        t.fire(field, "input", TestUI.map("value", "typed"));
        assertEquals("typed", input.getInputText());
        Button ok = button(d, "dialog-ok");
        t.run(new Runnable() {
            @Override
            public void run() {
                input.setValid(false);
            }
        });
        assertFalse(ok.isEnabled());
        assertTrue("cancel stays enabled", button(d, "dialog-cancel").isEnabled());
        t.run(new Runnable() {
            @Override
            public void run() {
                input.setValid(true);
                input.createNotificationLineSupport().setWarningMessage("careful");
            }
        });
        assertTrue(ok.isEnabled());
        assertEquals("careful", d.get(DialogComponent.NOTIFICATION));
        assertEquals("warning", d.get(DialogComponent.NOTIFICATION_KIND));
        click(ok);
        assertSame(NotifyDescriptor.OK_OPTION, futures.get(0).get(1, TimeUnit.SECONDS));
        assertEquals("typed", input.getInputText());
    }

    @Test
    public void dialogDescriptorButtonListenerAndClosingOptions() throws Exception {
        final List<String> commands = new ArrayList<String>();
        final Label content = new Label("form");
        final DialogDescriptor dd = new DialogDescriptor(content, "Form", false, new Object[] {"Apply", NotifyDescriptor.OK_OPTION, NotifyDescriptor.CANCEL_OPTION},
                NotifyDescriptor.OK_OPTION, DialogDescriptor.BOTTOM_ALIGN, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        commands.add(e.getActionCommand());
                    }
                });
        dd.setClosingOptions(new Object[] {NotifyDescriptor.OK_OPTION, NotifyDescriptor.CANCEL_OPTION});
        final List<Dialog> dialogs = new ArrayList<Dialog>();
        t.run(new Runnable() {
            @Override
            public void run() {
                Dialog dlg = DialogDisplayer.getDefault().createDialog(dd);
                dialogs.add(dlg);
                assertFalse(dlg.isVisible());
                dlg.show();
            }
        });
        DialogComponent d = dialog();
        assertSame(content, d.getSlot(DialogComponent.CONTENT));
        assertFalse("not modal", d.isModal());
        assertNull(t.ui.getModal());
        click(button(d, "dialog-apply"));
        assertEquals(Arrays.asList("Apply"), commands);
        assertTrue("Apply does not close", d.isAttached());
        assertEquals("Apply", dd.getValue());
        t.run(new Runnable() {
            @Override
            public void run() {
                dialogs.get(0).setTitle("Form 2");
            }
        });
        assertEquals("Form 2", d.getTitle());
        click(button(d, "dialog-ok"));
        assertEquals(Arrays.asList("Apply", "OK"), commands);
        assertFalse(d.isAttached());
        assertSame(NotifyDescriptor.OK_OPTION, dialogs.get(0).getResult().get(1, TimeUnit.SECONDS));
    }

    static final class Step extends WizardDescriptor.AbstractPanel<Map<String, Object>> {
        final Label label;
        boolean valid;
        final List<String> log;

        Step(String name, boolean valid, List<String> log) {
            super(name);
            this.label = new Label(name);
            this.valid = valid;
            this.log = log;
        }

        @Override
        public Component getComponent() {
            return label;
        }

        @Override
        public boolean isValid() {
            return valid;
        }

        @Override
        public void readSettings(Map<String, Object> settings) {
            log.add("read:" + getName());
        }

        @Override
        public void storeSettings(Map<String, Object> settings) {
            log.add("store:" + getName());
            settings.put(getName(), Boolean.TRUE);
        }

        void setValid(boolean v) {
            valid = v;
            fireChange();
        }
    }

    @Test
    public void wizardNavigatesAndFinishes() throws Exception {
        final List<String> log = new ArrayList<String>();
        final Step one = new Step("One", false, log);
        final Step two = new Step("Two", true, log);
        final Map<String, Object> settings = new java.util.LinkedHashMap<String, Object>();
        final WizardDescriptor<Map<String, Object>> w = new WizardDescriptor<Map<String, Object>>(
                new WizardDescriptor.ArrayIterator<Map<String, Object>>(one, two), settings);
        w.setTitle("Wizard");
        final List<CompletableFuture<Object>> futures = new ArrayList<CompletableFuture<Object>>();
        t.run(new Runnable() {
            @Override
            public void run() {
                futures.add(DialogDisplayer.getDefault().notify(w));
            }
        });
        DialogComponent d = dialog();
        assertEquals("Wizard – One", d.getTitle());
        assertSame(one.label, d.getSlot(DialogComponent.CONTENT));
        assertNotNull(d.getSlot(DialogComponent.SIDE));
        Button next = button(d, "dialog-next");
        Button back = button(d, "dialog-back");
        Button finish = button(d, "dialog-finish");
        assertFalse(next.isEnabled());
        assertFalse(back.isEnabled());
        assertFalse(finish.isEnabled());
        assertEquals(Arrays.asList("read:One"), log);
        t.run(new Runnable() {
            @Override
            public void run() {
                one.setValid(true);
            }
        });
        assertTrue(next.isEnabled());
        assertFalse("not the last step", finish.isEnabled());
        click(next);
        assertEquals(Arrays.asList("read:One", "store:One", "read:Two"), log);
        assertSame(two.label, d.getSlot(DialogComponent.CONTENT));
        assertTrue(back.isEnabled());
        assertFalse(next.isEnabled());
        assertTrue(finish.isEnabled());
        click(back);
        assertSame(one.label, d.getSlot(DialogComponent.CONTENT));
        click(next);
        t.run(new Runnable() {
            @Override
            public void run() {
                w.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, "almost");
            }
        });
        assertEquals("almost", d.get(DialogComponent.NOTIFICATION));
        click(finish);
        assertSame(WizardDescriptor.FINISH_OPTION, futures.get(0).get(1, TimeUnit.SECONDS));
        assertEquals(Boolean.TRUE, settings.get("Two"));
        assertFalse(d.isAttached());
    }

    @Test
    public void notificationsFanOutToEveryTabAndWaitInTheDrawer() throws Exception {
        final UI second = t.openUI("u2");
        NotificationHost host1 = t.ui.getLookup().lookup(NotificationHost.class);
        NotificationHost host2 = second.getLookup().lookup(NotificationHost.class);
        assertNotNull(host1);
        assertNotNull(host2);
        NotificationsCell cell1 = t.ui.getLookup().lookup(NotificationsCell.class);
        assertNotNull(cell1);
        assertEquals(Integer.valueOf(0), cell1.get(NotificationsCell.COUNT));
        final List<String> clicked = new ArrayList<String>();
        // from a background thread
        final NotificationDisplayer nd = NotificationDisplayer.getDefault(t.session);
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                nd.notify("Done", null, "all good", new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        clicked.add("clicked on " + e.getUI().getId());
                    }
                }, NotificationDisplayer.Priority.NORMAL, NotificationDisplayer.Category.INFO);
            }
        });
        thread.start();
        thread.join();
        assertEquals(1, t.callsOf(host1, "add").size());
        assertEquals(1, t.callsOf(host2, "add").size());
        assertEquals(Integer.valueOf(1), cell1.get(NotificationsCell.COUNT));
        assertEquals(1, nd.getNotifications().size());
        Notification n = nd.getNotifications().get(0);
        assertEquals("Done", n.getTitle());
        TestUI.fire(second, host2, "clickItem", TestUI.map("id", 1));
        assertEquals(Arrays.asList("clicked on u2"), clicked);
        t.fire(host1, "dismiss", TestUI.map("id", 1));
        assertTrue(nd.getNotifications().isEmpty());
        assertEquals(1, t.callsOf(host2, "remove").size());
        assertEquals(Integer.valueOf(0), cell1.get(NotificationsCell.COUNT));
        // posted while a third tab does not exist yet: it gets the drawer content on attach
        nd.notify("Later", "", NotificationDisplayer.Category.WARNING);
        UI third = t.openUI("u3");
        NotificationHost host3 = third.getLookup().lookup(NotificationHost.class);
        List<Map<String, Object>> init = t.callsOf(host3, "init");
        assertFalse(init.isEmpty());
        assertTrue(init.get(init.size() - 1).toString().contains("Later"));
        nd.clearAll();
        assertEquals(1, t.callsOf(host3, "clearAll").size());
    }

    @Test
    public void progressHandleDrivesTheStatusCell() throws Exception {
        ProgressStatusCell cell = (ProgressStatusCell) TestUI.byTestId(t.ui.getContent(), "progress-cell");
        assertNotNull(cell);
        assertEquals(Integer.valueOf(0), cell.get(ProgressStatusCell.COUNT));
        final List<String> cancelled = new ArrayList<String>();
        ProgressHandle h = ProgressHandleFactory.createHandle("Scan", new Cancellable() {
            @Override
            public boolean cancel() {
                cancelled.add("yes");
                return true;
            }
        }, t.session);
        h.start(10);
        h.progress("sector 3", 3);
        waitFor(cell, ProgressStatusCell.COUNT, 1);
        t.run(new Runnable() {
            @Override
            public void run() {
                assertEquals("Scan: sector 3", cell.get(ProgressStatusCell.TEXT));
                assertEquals(0.3, cell.get(ProgressStatusCell.VALUE), 0.001);
                assertTrue(cell.get(ProgressStatusCell.CANCELLABLE));
            }
        });
        ProgressHandle other = ProgressHandleFactory.createHandle("Other", null, t.session);
        other.start();
        waitFor(cell, ProgressStatusCell.COUNT, 2);
        t.run(new Runnable() {
            @Override
            public void run() {
                assertEquals("newest first", "Other", cell.get(ProgressStatusCell.TEXT));
                assertEquals(-1.0, cell.get(ProgressStatusCell.VALUE), 0.001);
                assertFalse(cell.get(ProgressStatusCell.CANCELLABLE));
            }
        });
        t.fire(cell, "cancel", TestUI.map("index", 0));
        assertEquals(Arrays.asList("yes"), cancelled);
        assertTrue(h.isCancelled());
        other.finish();
        h.finish();
        waitFor(cell, ProgressStatusCell.COUNT, 0);
        t.run(new Runnable() {
            @Override
            public void run() {
                assertNull(cell.get(ProgressStatusCell.TEXT));
            }
        });
    }

    /**
     * Polls a property from the test thread until the coalescing timer has pushed it. The cell's other
     * properties are read on the session executor afterwards ({@code t.run}): the push sets them one after
     * another, so a read from this thread right after the poll could land between two of the sets.
     */
    private <T> void waitFor(final Component c, final org.webbeans.ui.Property<T> p, final T expected) throws InterruptedException {
        long end = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < end) {
            if (expected.equals(c.get(p))) {
                return;
            }
            Thread.sleep(20);
        }
        t.run(new Runnable() {
            @Override
            public void run() {
                assertEquals(expected, c.get(p));
            }
        });
    }
}
