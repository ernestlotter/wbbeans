package org.webbeans.windows;

import org.webbeans.ui.UI;

/**
 * Lets modules layered above the window system (the actions module: menu bar, toolbars, shortcuts,
 * notifications) complete the {@link MainWindow} that {@link MainWindowProvider#build} creates. Register with
 * {@code @ServiceProvider(service = MainWindowDecorator.class)}; decorators run in lookup order after the dock
 * and status bar exist and before the layout is restored.
 */
public interface MainWindowDecorator {

    void decorate(UI ui, MainWindow main);
}
