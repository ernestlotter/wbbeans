package org.webbeans.actions;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Container;

/** The main menu bar ({@code wb.MenuBar}): its children are top-level {@link Menu}s. */
@Renderer(id = "wb.MenuBar", resource = "org/webbeans/actions/client/menubar.js", css = "org/webbeans/actions/client/actions.css")
public class MenuBar extends Container {

    public MenuBar() {
        super("wb.MenuBar");
    }

    /** The top-level menu with the given text, or null. */
    public Menu findMenu(String text) {
        for (org.webbeans.ui.Component c : getChildren()) {
            if (c instanceof Menu && text.equals(((Menu) c).getText())) {
                return (Menu) c;
            }
        }
        return null;
    }
}
