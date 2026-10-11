package org.webbeans.tutorial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.webbeans.actions.Action;
import org.webbeans.actions.Actions;
import org.webbeans.nodes.Node;
import org.webbeans.test.HeadlessUI;
import org.webbeans.ui.components.Label;

/** Tutorial §11: driving the tutorial windows without a browser. */
public class TutorialTest {

    @Test
    public void helloGreetsByName() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            h.open("HelloTC");
            h.type("hello-name", "Ada");                 // the browser would send a "change" event
            h.click("hello-greet");
            assertEquals("Hello, Ada!", ((Label) h.byTestId("hello-greeting")).getText());
        }
    }

    @Test
    public void addTaskActionAsksAndAddsANode() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            TasksTopComponent tasks = (TasksTopComponent) h.open("TasksTC");
            final Action add = Actions.forID("Tasks", "org.webbeans.tutorial.AddTaskAction");
            h.run(new Runnable() {
                @Override
                public void run() {
                    Actions.invoke(add, null, h.globalContext());
                }
            });
            assertTrue(h.hasDialog());
            h.type("dialog-input", "Write tests");
            h.answerDialog("dialog-ok");
            Node[] kids = tasks.getExplorerManager().getRootContext().getChildren().getNodes();
            assertEquals(3, kids.length);
            assertEquals("Write tests", kids[2].getName());
        }
    }

    @Test
    public void f1OpensTheTutorialHelpPage() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            h.activate(h.open("HelloTC"));
            final Action f1 = Actions.forID("Help", "org.webbeans.help.ContextHelpAction");
            h.run(new Runnable() {
                @Override
                public void run() {
                    Actions.invoke(f1, null, h.globalContext());
                }
            });
            org.webbeans.help.HelpTopComponent help = (org.webbeans.help.HelpTopComponent) h.wm().findTopComponent("HelpTC");
            assertTrue(help.isOpened());
            assertEquals("tutorial", help.getCurrentTopic().getId());
            assertTrue(help.getPageHtml().contains("<h1 id=\"tutorial-windows\">Tutorial windows</h1>"));
        }
    }
}
