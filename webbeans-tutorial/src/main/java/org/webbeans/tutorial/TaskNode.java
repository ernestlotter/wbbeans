package org.webbeans.tutorial;

import java.io.IOException;
import java.util.List;

import org.openide.util.lookup.Lookups;
import org.webbeans.actions.Action;
import org.webbeans.actions.Actions;
import org.webbeans.core.i18n.Messages;
import org.webbeans.nodes.AbstractNode;
import org.webbeans.nodes.Children;
import org.webbeans.nodes.PropertySupport;
import org.webbeans.nodes.Sheet;

/** Tutorial §6: a task as a node — the task in the lookup, rename and delete, a "done" property, popup actions. */
@Messages({"PROP_Title=Title", "HINT_Title=What to do", "PROP_Done=Done", "HINT_Done=Finished?"})
public final class TaskNode extends AbstractNode {

    public static final String ACTIONS_FOLDER = "Tasks/Actions";

    private final Task task;

    public TaskNode(Task task) {
        super(Children.LEAF, Lookups.singleton(task));
        this.task = task;
        setName(task.getTitle());
        setIconBase("org/webbeans/tutorial/" + (task.isDone() ? "done.svg" : "task.svg"));
        setCanRename(true);
        setCanDestroy(true);
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> all = Actions.forPath(ACTIONS_FOLDER);
        return all.toArray(new Action[all.size()]);
    }

    @Override
    public void setName(String n) {          // in-place rename in the view goes through the model
        if (task != null && !task.getTitle().equals(n)) {
            TaskList.get().rename(task, n);
        }
        super.setName(n);
    }

    @Override
    public void destroy() throws IOException {   // Edit ▸ Delete / the Delete key
        TaskList.get().remove(task);
        super.destroy();
    }

    @Override
    public String getHtmlDisplayName() {
        return task.isDone() ? "<s>" + task.getTitle() + "</s>" : null;
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = new Sheet();
        Sheet.Set set = Sheet.createPropertiesSet();
        set.put(new PropertySupport.Name(this, Bundle.PROP_Title(), Bundle.HINT_Title()));
        set.put(new PropertySupport.ReadWrite<Boolean>("done", Boolean.class, Bundle.PROP_Done(), Bundle.HINT_Done()) {
            @Override
            public Boolean getValue() {
                return task.isDone();
            }

            @Override
            public void setValue(Boolean v) {
                TaskList.get().setDone(task, Boolean.TRUE.equals(v));
            }
        });
        sheet.put(set);
        return sheet;
    }
}
