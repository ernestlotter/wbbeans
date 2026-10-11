package org.webbeans.tutorial;

import org.webbeans.actions.ActionMap;
import org.webbeans.core.i18n.Messages;
import org.webbeans.explorer.ExplorerManager;
import org.webbeans.explorer.ExplorerUtils;
import org.webbeans.explorer.OutlineView;
import org.webbeans.nodes.AbstractNode;
import org.webbeans.nodes.Children;
import org.webbeans.nodes.Node;
import org.webbeans.ui.components.Layout;
import org.webbeans.windows.TopComponent;

/** Tutorial §6: an explorer window — an OutlineView over the task nodes, selection and actions in the lookup. */
@TopComponent.Registration(mode = "explorer", openAtStartup = true, position = 150)
@TopComponent.Description(preferredID = "TasksTC", iconBase = "org/webbeans/tutorial/task.svg", helpId = "tutorial")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenTasks", position = 150)
@Messages({"CTL_TasksTC=Tasks", "CTL_OpenTasks=Tasks", "COL_Title=Task", "COL_Done=Done"})
public final class TasksTopComponent extends TopComponent implements ExplorerManager.Provider, TaskList.Listener {

    private final ExplorerManager em = new ExplorerManager();
    private final ActionMap map = new ActionMap();
    private final OutlineView view = new OutlineView(Bundle.COL_Title());
    private final TaskChildren children = new TaskChildren();

    public TasksTopComponent() {
        setDisplayName(Bundle.CTL_TasksTC());
        setLayout(new Layout.Border());
        ExplorerUtils.activateActions(em, map, true);          // copy/cut/paste/delete follow the selection
        associateLookup(ExplorerUtils.createLookup(em, map));  // selected nodes (+ their Tasks) in the window's lookup
        view.setPropertyColumns("done", Bundle.COL_Done());
        view.setColumnWidth("done", 60);
        view.setRootVisible(false);
        view.setTestId("tasks");
        AbstractNode root = new AbstractNode(children);
        root.setName("tasks");
        em.setRootContext(root);
        add(view, Layout.Border.CENTER);
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return em;
    }

    @Override
    protected void componentOpened() {
        TaskList.get().addListener(this);
        tasksChanged();
    }

    @Override
    protected void componentClosed() {
        TaskList.get().removeListener(this);
    }

    @Override
    public void tasksChanged() {
        children.setTasks(TaskList.get().getTasks());
    }

    @Override
    protected void localeChanged() {
        setDisplayName(Bundle.CTL_TasksTC());
    }

    /** Keys-based children: setKeys diffs the list and keeps the nodes of unchanged tasks. */
    static final class TaskChildren extends Children.Keys<Task> {
        void setTasks(java.util.List<Task> tasks) {
            setKeys(tasks);
            for (Task t : tasks) {
                refreshKey(t);              // title or done may have changed: recreate that node
            }
        }

        @Override
        protected Node[] createNodes(Task key) {
            return new Node[] {new TaskNode(key)};
        }
    }
}
