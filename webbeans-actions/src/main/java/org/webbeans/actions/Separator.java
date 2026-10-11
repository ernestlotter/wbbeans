package org.webbeans.actions;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;

/** A separator line in menus, popups and toolbars ({@code wb.Separator}). */
@Renderer(id = "wb.Separator", resource = "org/webbeans/actions/client/menu.js")
public class Separator extends Component {

    public Separator() {
        super("wb.Separator");
    }
}
