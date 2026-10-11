package org.webbeans.workbench.admin;

import org.openide.util.lookup.ServiceProvider;
import org.webbeans.ui.UI;
import org.webbeans.windows.LayoutRegistration;
import org.webbeans.windows.MainWindow;
import org.webbeans.windows.MainWindowDecorator;
import org.webbeans.windows.WindowManager;

/**
 * Administrators get their own window layout: the shipped {@code admin.json} (Server Status open at the bottom)
 * is used until they rearrange it; {@code WindowManager.setRole} switches to it when a tab opens.
 */
@LayoutRegistration(role = "admin", resource = "org/webbeans/workbench/admin/layouts/admin.json")
@ServiceProvider(service = MainWindowDecorator.class, position = 6000)
public final class AdminLayout implements MainWindowDecorator {

    @Override
    public void decorate(UI ui, MainWindow window) {
        if (ui.getSession().getPrincipal().hasRole("admin")) {
            final WindowManager wm = ui.getLookup().lookup(WindowManager.class);
            if (wm != null) {
                wm.invokeWhenUIReady(new Runnable() {
                    @Override
                    public void run() {
                        wm.setRole("admin");
                    }
                });
            }
        }
    }
}
