package org.webbeans.quicksearch;

import java.util.ArrayList;
import java.util.List;

import org.openide.util.Lookup;
import org.webbeans.actions.Action;
import org.webbeans.actions.ActionPresenters;
import org.webbeans.actions.Actions;
import org.webbeans.actions.ContextAwareAction;
import org.webbeans.actions.Utilities;
import org.webbeans.core.registry.Entry;
import org.webbeans.core.registry.Folder;
import org.webbeans.core.registry.Registration;
import org.webbeans.core.registry.Registry;
import org.webbeans.ui.UI;

/** Finds menu items ({@code Menu/**}) by name or initials; runs them with the global context. */
@Registration(path = QuickSearch.PROVIDERS, type = QuickSearchProvider.class, position = 100)
public final class ActionsProvider implements QuickSearchProvider {

    @Override
    public String getCategory() {
        return org.webbeans.core.i18n.Bundles.getMessage(QuickSearch.class, "LBL_Actions");
    }

    @Override
    public void search(String query, Response response) {
        final UI ui = UI.current();
        for (Folder top : Registry.getDefault().folder("Menu").getFolders()) {
            List<Entry> entries = new ArrayList<Entry>();
            collect(top, entries);
            for (Entry e : entries) {
                final Action a;
                try {
                    a = Actions.actionFor(e);
                } catch (RuntimeException ex) {
                    continue;
                }
                if (a == null) {
                    continue;
                }
                String name = Actions.menuText(a);
                if (name == null || !QuickSearch.matches(name.replace("...", "").replace("…", ""), query)) {
                    continue;
                }
                String path = top.getDisplayName() + " › " + name.replace("...", "").replace("…", "");
                String accel = (String) a.getValue(Action.ACCELERATOR);
                if (!response.add(name.replace("...", "").replace("…", ""), accel != null ? path + "  (" + Utilities.keyToDisplay(accel) + ")" : path, a.getIcon(), new Runnable() {
                    @Override
                    public void run() {
                        Lookup ctx = ui != null ? Utilities.actionsGlobalContext(ui) : Lookup.EMPTY;
                        Action inst = a instanceof ContextAwareAction ? ((ContextAwareAction) a).createContextAwareInstance(ctx) : a;
                        try {
                            if (inst.isEnabled()) {
                                Actions.invoke(inst, null, ctx);
                            }
                        } finally {
                            if (inst instanceof ActionPresenters.Disposable) {
                                ((ActionPresenters.Disposable) inst).dispose();
                            }
                        }
                    }
                })) {
                    return;
                }
            }
        }
    }

    private static void collect(Folder f, List<Entry> out) {
        for (Entry e : f.getEntries()) {
            if (e.getKind() != Entry.Kind.SEPARATOR) {
                out.add(e);
            }
        }
        for (Folder sub : f.getFolders()) {
            collect(sub, out);
        }
    }
}
