package org.webbeans.demo;

import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.core.i18n.Messages;
import org.webbeans.scene3d.Line;
import org.webbeans.scene3d.Node3D;
import org.webbeans.scene3d.Scene3D;
import org.webbeans.scene3d.Scene3DTopComponent;

/** A context action over {@link Scene3D} (the 3D window puts its scene in the lookup): shows or hides every line node. */
@ActionID(category = "Scene3D", id = "org.webbeans.demo.ToggleOrbitsAction")
@ActionRegistration(displayName = "#CTL_ToggleOrbits", iconBase = "org/webbeans/demo/icons/orbit.svg")
@ActionReference(path = Scene3DTopComponent.ACTIONS_FOLDER, position = 100)
@Messages({"CTL_ToggleOrbits=Toggle orbit lines"})
public final class ToggleOrbitsAction implements ActionListener {

    private final Scene3D scene;

    public ToggleOrbitsAction(Scene3D scene) {
        this.scene = scene;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Boolean show = null;
        for (Node3D n : scene.getNodes()) {
            if (n instanceof Line) {
                if (show == null) {
                    show = !n.isVisible();
                }
                n.setVisible(show);
            }
        }
    }
}
