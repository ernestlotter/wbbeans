package org.webbeans.actions;

import org.openide.util.Lookup;
import org.webbeans.core.registry.Entry;
import org.webbeans.core.registry.Folder;
import org.webbeans.core.registry.Registry;
import org.webbeans.ui.Component;

/**
 * Builds the {@link ToolbarPanel} from the registry folder {@code Toolbars}: every sub-folder is a toolbar
 * (hidden ones — user overlay — are skipped by the registry), its entries buttons and separators.
 */
public final class ToolbarBuilder {

    public static final String TOOLBARS_ROOT = "Toolbars";

    private ToolbarBuilder() {
    }

    public static ToolbarPanel build(Lookup context) {
        ToolbarPanel panel = new ToolbarPanel();
        for (Folder f : Registry.getDefault().folder(TOOLBARS_ROOT).getFolders()) {
            Toolbar t = toolbar(f, context);
            if (t.getChildCount() > 0) {
                panel.add(t);
            }
        }
        return panel;
    }

    public static Toolbar toolbar(Folder folder, Lookup context) {
        Toolbar t = new Toolbar(folder.getDisplayName());
        boolean pendingSeparator = false;
        for (Entry e : folder.getEntries()) {
            if (e.getKind() == Entry.Kind.SEPARATOR) {
                pendingSeparator = t.getChildCount() > 0;
                continue;
            }
            Component c;
            Action a = Actions.actionFor(e);
            if (a != null) {
                c = ActionPresenters.toolbarButton(a, context);
            } else {
                Entry target = e.getTarget();
                Class<?> declared = target == null ? null : target.getInstanceOf();
                c = target != null && (declared == null || Component.class.isAssignableFrom(declared)) ? target.newInstance(Component.class) : null;
                if (c == null && target != null && (declared == null || Presenter.Toolbar.class.isAssignableFrom(declared))) {
                    Presenter.Toolbar p = target.getInstance(Presenter.Toolbar.class);
                    c = p == null ? null : p.getToolbarPresenter();
                }
            }
            if (c == null) {
                continue;
            }
            if (pendingSeparator) {
                t.add(new Separator());
                pendingSeparator = false;
            }
            t.add(c);
        }
        return t;
    }
}
