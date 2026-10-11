package org.webbeans.actions;

import org.webbeans.ui.Component;

/**
 * Implemented by actions that provide their own presenter component instead of the default menu item or
 * toolbar button (a submenu, a combo box in a toolbar, ...). The builders ask for a fresh component per UI.
 */
public interface Presenter {

    interface Menu {
        /** A {@link MenuItem}, a {@link Menu} (submenu) or any component to place in a menu. */
        Component getMenuPresenter();
    }

    interface Toolbar {
        Component getToolbarPresenter();
    }

    interface Popup {
        Component getPopupPresenter();
    }
}
