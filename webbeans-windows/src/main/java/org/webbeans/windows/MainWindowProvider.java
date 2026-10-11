package org.webbeans.windows;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.lookup.ServiceProvider;
import org.webbeans.core.registry.Entry;
import org.webbeans.core.registry.Registry;
import org.webbeans.ui.Component;
import org.webbeans.ui.UI;
import org.webbeans.ui.UIProvider;

/**
 * The default page: a {@link MainWindow} with the dock and a status bar, the window manager installed and the
 * user's layout restored. Registered last ({@code position = 10000}) so an application's own {@code UIProvider}
 * wins; applications that want the main window plus extras can call {@link #build(UI)} from theirs.
 */
@ServiceProvider(service = UIProvider.class, position = 10000)
public final class MainWindowProvider implements UIProvider {

    private static final Logger LOG = Logger.getLogger(MainWindowProvider.class.getName());
    /** Registry folder of status bar cells: {@code @Registration(path = "StatusBar", type = Component.class)} on a static factory method, one instance per UI. */
    public static final String STATUS_BAR_PATH = "StatusBar";

    @Override
    public void init(UI ui) {
        build(ui);
    }

    /** Builds the main window into the UI, installs the window manager and restores the layout; returns the window. */
    public static MainWindow build(UI ui) {
        WindowManagerImpl wm = WindowManagerImpl.install(ui);
        MainWindow main = new MainWindow();
        StatusBar status = new StatusBar();
        ui.addToLookup(new StatusDisplayer(ui, status));
        main.setDock(wm.getDock());
        for (Entry e : Registry.getDefault().folder(STATUS_BAR_PATH).getEntries()) {
            try {
                Component cell = e.newInstance(Component.class);
                if (cell != null) {
                    status.add(cell);
                }
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Status bar cell " + e.getPath() + " failed", ex);
            }
        }
        main.setStatusBar(status);
        ui.setContent(main);
        wm.bindShortcuts();
        for (MainWindowDecorator d : org.openide.util.Lookup.getDefault().lookupAll(MainWindowDecorator.class)) {
            try {
                d.decorate(ui, main);
            } catch (RuntimeException ex) {
                LOG.log(Level.SEVERE, "Main window decorator " + d + " failed", ex);
            }
        }
        wm.restore();
        return main;
    }
}
