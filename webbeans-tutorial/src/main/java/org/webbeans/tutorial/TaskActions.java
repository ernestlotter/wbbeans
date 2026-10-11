package org.webbeans.tutorial;

import java.util.List;
import java.util.function.Consumer;

import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionReferences;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.core.i18n.Messages;
import org.webbeans.core.registry.FolderRegistration;
import org.webbeans.dialogs.DialogDisplayer;
import org.webbeans.dialogs.NotifyDescriptor;
import org.webbeans.windows.StatusDisplayer;

/** Tutorial §7: an always-enabled action with a dialog, and a context action over the selected tasks. */
@Messages({"CTL_AddTask=Add Task…", "CTL_Done=Mark as Done", "LBL_Title=Title", "TITLE_AddTask=New task",
    "MSG_Done={0} task(s) done", "CTL_TasksToolbar=Tasks"})
@FolderRegistration(path = "Toolbars/Tasks", displayName = "#CTL_TasksToolbar", position = 500)
public final class TaskActions {

    private TaskActions() {
    }

    /** Always enabled: no constructor parameter. In the Tools menu, the Tasks toolbar, Ctrl+Shift+T and the node popup. */
    @ActionID(category = "Tasks", id = "org.webbeans.tutorial.AddTaskAction")
    @ActionRegistration(displayName = "#CTL_AddTask", iconBase = "org/webbeans/tutorial/task.svg")
    @ActionReferences({
        @ActionReference(path = "Menu/Tools", position = 400),
        @ActionReference(path = "Toolbars/Tasks", position = 100),
        @ActionReference(path = "Shortcuts", name = "DS-T"),
        @ActionReference(path = TaskNode.ACTIONS_FOLDER, position = 200)})
    public static final class AddTaskAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            final NotifyDescriptor.InputLine in = new NotifyDescriptor.InputLine(Bundle.LBL_Title(), Bundle.TITLE_AddTask());
            DialogDisplayer.getDefault().notify(in).thenAccept(new Consumer<Object>() {
                @Override
                public void accept(Object answer) {
                    String title = in.getInputText() == null ? "" : in.getInputText().trim();
                    if (answer == NotifyDescriptor.OK_OPTION && !title.isEmpty()) {
                        TaskList.get().add(title);
                    }
                }
            });
        }
    }

    /** Context action: enabled while the global context holds at least one Task (the selection of the Tasks window). */
    @ActionID(category = "Tasks", id = "org.webbeans.tutorial.DoneAction")
    @ActionRegistration(displayName = "#CTL_Done", iconBase = "org/webbeans/tutorial/done.svg")
    @ActionReferences({
        @ActionReference(path = "Menu/Tools", position = 410),
        @ActionReference(path = "Toolbars/Tasks", position = 110),
        @ActionReference(path = TaskNode.ACTIONS_FOLDER, position = 100)})
    public static final class DoneAction implements ActionListener {
        private final List<Task> tasks;

        public DoneAction(List<Task> tasks) {
            this.tasks = tasks;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            for (Task t : tasks) {
                TaskList.get().setDone(t, true);
            }
            StatusDisplayer.getDefault().setStatusText(Bundle.MSG_Done(tasks.size()));
        }
    }
}
