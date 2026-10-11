package org.webbeans.actions;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Container;

/** The row of toolbars under the menu bar ({@code wb.ToolbarPanel}); children are {@link Toolbar}s. */
@Renderer(id = "wb.ToolbarPanel", resource = "org/webbeans/actions/client/toolbar.js", css = "org/webbeans/actions/client/actions.css")
public class ToolbarPanel extends Container {

    public ToolbarPanel() {
        super("wb.ToolbarPanel");
    }
}
