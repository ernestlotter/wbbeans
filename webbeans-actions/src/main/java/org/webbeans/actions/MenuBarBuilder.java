package org.webbeans.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.openide.util.Lookup;
import org.webbeans.core.registry.Entry;
import org.webbeans.core.registry.Folder;
import org.webbeans.core.registry.Registry;
import org.webbeans.ui.Component;
import org.webbeans.ui.Resource;

/**
 * Builds a {@link MenuBar} from the registry folder {@code Menu}: every sub-folder is a top-level menu (its
 * {@code displayName} attribute, see {@code @FolderRegistration}); entries and sub-folders inside are items
 * and submenus in position order, {@code null}/separator entries become {@link Separator}s. Menus are filled
 * when first opened, so the bar costs one item per top-level menu at login. The current registry context
 * (roles, locale, user overlay) decides what is visible.
 */
public final class MenuBarBuilder {

    public static final String MENU_ROOT = "Menu";

    private MenuBarBuilder() {
    }

    /** The menu bar for the UI whose actions run over the context (normally {@link Utilities#actionsGlobalContext(org.webbeans.ui.UI)}). */
    public static MenuBar build(Lookup context) {
        MenuBar bar = new MenuBar();
        for (Folder f : Registry.getDefault().folder(MENU_ROOT).getFolders()) {
            if (f.getEntries().isEmpty() && f.getFolders().isEmpty()) {
                continue; // a declared but (for this user) empty menu is not shown
            }
            bar.add(menu(f, context));
        }
        return bar;
    }

    /** A lazily filled menu for a registry folder. */
    public static Menu menu(final Folder folder, final Lookup context) {
        final Menu m = new Menu(folder.getDisplayName());
        Object icon = folder.getAttributes().get("iconBase");
        if (icon instanceof String && !((String) icon).isEmpty()) {
            m.setIcon(Resource.of((String) icon));
        }
        m.setOnOpen(new Runnable() {
            @Override
            public void run() {
                fill(m, folder, context);
            }
        });
        return m;
    }

    /** Fills a menu (or popup) container with the folder's content. */
    public static void fill(org.webbeans.ui.Container into, Folder folder, Lookup context) {
        List<Object[]> items = new ArrayList<Object[]>();
        for (Folder sub : folder.getFolders()) {
            items.add(new Object[] {sub.getPosition(), sub.getName(), sub});
        }
        for (Entry e : folder.getEntries()) {
            items.add(new Object[] {e.getPosition(), e.getName(), e});
        }
        Collections.sort(items, new Comparator<Object[]>() {
            @Override
            public int compare(Object[] a, Object[] b) {
                int pa = (Integer) a[0];
                int pb = (Integer) b[0];
                if (pa != pb) {
                    return pa < pb ? -1 : 1;
                }
                return ((String) a[1]).compareTo((String) b[1]);
            }
        });
        boolean pendingSeparator = false;
        for (Object[] it : items) {
            Component c;
            if (it[2] instanceof Folder) {
                c = menu((Folder) it[2], context);
            } else {
                Entry e = (Entry) it[2];
                if (e.getKind() == Entry.Kind.SEPARATOR) {
                    pendingSeparator = into.getChildCount() > 0;
                    continue;
                }
                c = presenter(e, context);
                if (c == null) {
                    continue;
                }
            }
            if (pendingSeparator) {
                into.add(new Separator());
                pendingSeparator = false;
            }
            into.add(c);
        }
    }

    /** The menu presenter for an entry: the action's item, or the component the entry provides, or null. */
    static Component presenter(Entry e, Lookup context) {
        Action a = Actions.actionFor(e);
        if (a != null) {
            return ActionPresenters.menuItem(a, context);
        }
        Entry target = e.getTarget();
        if (target != null) {
            Class<?> declared = target.getInstanceOf();
            if (declared == null || Component.class.isAssignableFrom(declared)) {
                Component c = target.newInstance(Component.class);
                if (c != null) {
                    return c;
                }
            }
            if (declared == null || Presenter.Menu.class.isAssignableFrom(declared)) {
                Presenter.Menu p = target.getInstance(Presenter.Menu.class);
                if (p != null) {
                    return p.getMenuPresenter();
                }
            }
        }
        return null;
    }
}
