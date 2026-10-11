package org.webbeans.actions;

import org.webbeans.core.registry.FolderRegistration;

/** The standard top-level menus; modules add their own with {@code @FolderRegistration(path = "Menu/<name>")}. */
@FolderRegistration(path = "Menu/File", displayName = "#CTL_FileMenu", position = 100)
@FolderRegistration(path = "Menu/Edit", displayName = "#CTL_EditMenu", position = 200)
@FolderRegistration(path = "Menu/View", displayName = "#CTL_ViewMenu", position = 300)
@FolderRegistration(path = "Menu/Tools", displayName = "#CTL_ToolsMenu", position = 800)
@FolderRegistration(path = "Menu/Window", displayName = "#CTL_WindowMenu", position = 900)
@FolderRegistration(path = "Menu/Help", displayName = "#CTL_HelpMenu", position = 1000)
public final class StandardMenus {

    private StandardMenus() {
    }
}
