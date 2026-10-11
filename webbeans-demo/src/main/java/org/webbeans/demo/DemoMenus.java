package org.webbeans.demo;

import org.webbeans.core.i18n.Messages;
import org.webbeans.core.registry.FolderRegistration;

/** The demo's own menu and toolbars. */
@FolderRegistration(path = "Menu/Planets", displayName = "#CTL_PlanetsMenu", position = 250)
@FolderRegistration(path = "Toolbars/Planets", displayName = "#CTL_PlanetsToolbar", position = 200)
@FolderRegistration(path = "Toolbars/Edit", displayName = "#CTL_EditToolbar", position = 100)
@Messages({"CTL_PlanetsMenu=Planets", "CTL_PlanetsToolbar=Planets", "CTL_EditToolbar=Edit"})
public final class DemoMenus {

    private DemoMenus() {
    }
}
