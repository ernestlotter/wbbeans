/** Workbench 3D viewer: the turbine model, the viewer and parts windows, the viewer actions — and their help pages. */
@HelpPage(id = "viewer", parent = "workbench", title = "#HLP_Viewer", resource = "org/webbeans/workbench/viewer3d/help/viewer.md", position = 300,
        keywords = "#KW_Viewer")
@HelpPage(id = "viewer.parts", parent = "viewer", title = "#HLP_Parts", resource = "org/webbeans/workbench/viewer3d/help/parts.md", position = 100)
@Messages({"HLP_Viewer=3D Viewer", "KW_Viewer=turbine, blades, sensors, camera, snapshot, spin", "HLP_Parts=Parts"})
package org.webbeans.workbench.viewer3d;

import org.webbeans.core.i18n.Messages;
import org.webbeans.help.HelpPage;
