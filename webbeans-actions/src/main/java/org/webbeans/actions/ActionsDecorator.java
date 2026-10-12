package org.webbeans.actions;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.registry.UserOverlay;
import org.webbeans.notifications.NotificationDisplayer;
import org.webbeans.ui.Registration;
import org.webbeans.ui.UI;
import org.webbeans.ui.UISession;
import org.webbeans.windows.MainWindow;
import org.webbeans.windows.MainWindowDecorator;

/**
 * Completes the main window: the menu bar and toolbars from the registry, the shortcut map, the notification
 * host. Rebuilds the bars on a locale change, the toolbars when the user's overlay changes (View ▸ Toolbars), and
 * bars plus shortcuts when modules come or go ({@link UISession.RegistryChangedEvent}) — with a notification
 * offering a reload when the client bundle changed as well.
 */
@ServiceProvider(service = MainWindowDecorator.class)
public final class ActionsDecorator implements MainWindowDecorator {

    private static final Logger LOG = Logger.getLogger(ActionsDecorator.class.getName());

    @Override
    public void decorate(final UI ui, final MainWindow main) {
        final Lookup context = Utilities.actionsGlobalContext(ui);
        main.setMenuBar(MenuBarBuilder.build(context));
        main.setToolBar(ToolbarBuilder.build(context));
        final Registration[] shortcuts = {Shortcuts.install(ui)};
        NotificationDisplayer.getDefault(ui.getSession()).attach(ui);
        final Registration registry = ui.getSession().subscribe(UISession.RegistryChangedEvent.class, new Consumer<UISession.RegistryChangedEvent>() {
            @Override
            public void accept(final UISession.RegistryChangedEvent e) {
                ui.access(new Runnable() {
                    @Override
                    public void run() {
                        if (ui.getState() == UI.State.CLOSED) {
                            return;
                        }
                        main.setMenuBar(MenuBarBuilder.build(context));
                        main.setToolBar(ToolbarBuilder.build(context));
                        shortcuts[0].remove();
                        shortcuts[0] = Shortcuts.install(ui);
                        if (e.isClientChanged()) {
                            NotificationDisplayer.getDefault(ui.getSession()).notify(
                                    Bundles.getMessage(ActionsDecorator.class, "MSG_ModulesChanged"), null,
                                    Bundles.getMessage(ActionsDecorator.class, "MSG_ModulesChangedDetails"),
                                    new ActionListener() {
                                        @Override
                                        public void actionPerformed(ActionEvent ev) {
                                            ev.getUI().reload();
                                        }
                                    }, NotificationDisplayer.Priority.HIGH, NotificationDisplayer.Category.INFO);
                        }
                    }
                });
            }
        });
        final Registration locale = ui.getSession().subscribe(UISession.LocaleChangeEvent.class, new Consumer<UISession.LocaleChangeEvent>() {
            @Override
            public void accept(UISession.LocaleChangeEvent e) {
                ui.access(new Runnable() {
                    @Override
                    public void run() {
                        main.setMenuBar(MenuBarBuilder.build(context));
                        main.setToolBar(ToolbarBuilder.build(context));
                    }
                });
            }
        });
        final UserOverlay overlay = ui.getSession().getOverlay();
        final ChangeListener overlayListener = new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                ui.access(new Runnable() {
                    @Override
                    public void run() {
                        if (ui.getState() != UI.State.CLOSED) {
                            main.setToolBar(ToolbarBuilder.build(context));
                        }
                    }
                });
            }
        };
        overlay.addChangeListener(overlayListener);
        ui.addCloseListener(new Runnable() {
            @Override
            public void run() {
                overlay.removeChangeListener(overlayListener);
                locale.remove();
                registry.remove();
                shortcuts[0].remove();
            }
        });
        LOG.log(Level.FINE, "Actions installed on {0}", ui);
    }
}
