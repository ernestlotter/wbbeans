package org.webbeans.actions;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.registry.Folder;
import org.webbeans.core.registry.Registry;
import org.webbeans.core.registry.RegistryContext;
import org.webbeans.core.registry.UserOverlay;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.Listener;
import org.webbeans.ui.UISession;

/**
 * View ▸ Toolbars: a submenu with a check item per registered toolbar; unchecking hides the toolbar through
 * the user's registry overlay ({@code users/<u>/config/registry.json}), which rebuilds the toolbar panel.
 */
@ActionID(category = "View", id = "org.webbeans.actions.ToolbarsMenuAction")
@ActionRegistration(displayName = "#CTL_Toolbars", lazy = false)
@ActionReference(path = "Menu/View", position = 100)
public final class ToolbarsMenuAction extends AbstractAction implements Presenter.Menu {

    private static final Logger LOG = Logger.getLogger(ToolbarsMenuAction.class.getName());

    public ToolbarsMenuAction() {
        super(Bundles.getMessage(ToolbarsMenuAction.class, "CTL_Toolbars"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
    }

    @Override
    public Component getMenuPresenter() {
        final Menu menu = new Menu(Bundles.getMessage(ToolbarsMenuAction.class, "CTL_Toolbars"));
        // rebuilt on every open so the check marks reflect the overlay
        menu.addListener(Menu.OPEN, new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                menu.removeAll();
                fill(menu);
            }
        });
        return menu;
    }

    private void fill(Menu menu) {
        final UISession session = UISession.current();
        final UserOverlay overlay = session == null ? null : session.getOverlay();
        // list every toolbar, hidden ones too: evaluate the folder without the overlay
        RegistryContext ctx = RegistryContext.current();
        RegistryContext all = RegistryContext.of(ctx.getRoles(), ctx.getLocale(), null);
        final java.util.List<Folder> folders = new java.util.ArrayList<Folder>();
        RegistryContext.run(all, new Runnable() {
            @Override
            public void run() {
                folders.addAll(Registry.getDefault().folder(ToolbarBuilder.TOOLBARS_ROOT).getFolders());
            }
        });
        for (final Folder f : folders) {
            final boolean visible = overlay == null || !overlay.isHidden(f.getPath());
            MenuItem item = new MenuItem(f.getDisplayName()).setKind(MenuItem.Kind.CHECK).setSelected(visible);
            item.addActionListener(new Listener<Event>() {
                @Override
                public void onEvent(Event e) {
                    if (overlay == null) {
                        return;
                    }
                    if (visible) {
                        overlay.hide(f.getPath());
                    } else {
                        overlay.reset(f.getPath());
                    }
                    try {
                        overlay.save(session.getUserFiles().registryOverlayFile());
                    } catch (IOException ex) {
                        LOG.log(Level.WARNING, "Cannot save the registry overlay", ex);
                    }
                }
            });
            menu.add(item);
        }
    }

    /** Programmatic toggle (used by tests and other modules). */
    public static void setToolbarVisible(UISession session, String toolbarName, boolean visible) {
        UserOverlay overlay = session.getOverlay();
        String path = ToolbarBuilder.TOOLBARS_ROOT + "/" + toolbarName;
        if (visible) {
            overlay.reset(path);
        } else {
            overlay.hide(path);
        }
        try {
            overlay.save(session.getUserFiles().registryOverlayFile());
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Cannot save the registry overlay", ex);
        }
    }

}
