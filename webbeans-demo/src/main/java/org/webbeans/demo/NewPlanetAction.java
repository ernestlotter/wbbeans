package org.webbeans.demo;

import java.util.function.Consumer;

import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionReferences;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.core.i18n.Messages;
import org.webbeans.dialogs.DialogDisplayer;
import org.webbeans.dialogs.NotifyDescriptor;

/** An always-enabled action (no-arg constructor): asks for a name with an input dialog and adds the planet. */
@ActionID(category = "Planets", id = "org.webbeans.demo.NewPlanetAction")
@ActionRegistration(displayName = "#CTL_NewPlanet", iconBase = "org/webbeans/demo/icons/planet.svg", menuText = "#CTL_NewPlanetMenu")
@ActionReferences({
    @ActionReference(path = "Menu/File", position = 100, separatorAfter = 150),
    @ActionReference(path = "Toolbars/Planets", position = 50),
    @ActionReference(path = "Shortcuts", name = "D-N")})
@Messages({"CTL_NewPlanet=New planet", "CTL_NewPlanetMenu=New Planet...", "LBL_PlanetName=Name of the new planet:", "TITLE_NewPlanet=New planet",
    "ERR_PlanetExists=A planet named {0} exists already"})
public final class NewPlanetAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        final NotifyDescriptor.InputLine input = new NotifyDescriptor.InputLine(Bundle.LBL_PlanetName(), Bundle.TITLE_NewPlanet());
        input.setInputText("Planet " + (SolarSystem.get().getPlanets().size() + 1));
        DialogDisplayer.getDefault().notify(input).thenAccept(new Consumer<Object>() {
            @Override
            public void accept(Object option) {
                if (option != NotifyDescriptor.OK_OPTION || input.getInputText().trim().isEmpty()) {
                    return;
                }
                String name = input.getInputText().trim();
                if (SolarSystem.get().find(name) != null) {
                    DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(Bundle.ERR_PlanetExists(name), NotifyDescriptor.ERROR_MESSAGE));
                    return;
                }
                PlanetsTopComponent.addPlanet(name);
            }
        });
    }
}
