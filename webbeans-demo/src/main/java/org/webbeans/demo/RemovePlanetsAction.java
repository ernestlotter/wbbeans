package org.webbeans.demo;

import java.util.List;
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
import org.webbeans.ui.UISession;

/** A multi-context action ({@code List<Planet>}): removes the selected planets after a confirmation dialog. */
@ActionID(category = "Planets", id = "org.webbeans.demo.RemovePlanetsAction")
@ActionRegistration(displayName = "#CTL_RemovePlanets", iconBase = "org/webbeans/actions/client/delete.svg")
@ActionReferences({
    @ActionReference(path = "Menu/Planets", position = 200, separatorAfter = 250),
    @ActionReference(path = "Toolbars/Planets", position = 200),
    @ActionReference(path = PlanetNode.ACTIONS_FOLDER, position = 200)})
@Messages({"CTL_RemovePlanets=Remove planets", "MSG_ConfirmRemove=Remove {0} planet(s) from the solar system?", "TITLE_Remove=Remove", "MSG_Removed=Removed {0}"})
public final class RemovePlanetsAction implements ActionListener {

    private final List<Planet> planets;

    public RemovePlanetsAction(List<Planet> planets) {
        this.planets = planets;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        NotifyDescriptor.Confirmation c = new NotifyDescriptor.Confirmation(Bundle.MSG_ConfirmRemove(planets.size()), Bundle.TITLE_Remove(),
                NotifyDescriptor.YES_NO_OPTION);
        DialogDisplayer.getDefault().notify(c).thenAccept(new Consumer<Object>() {
            @Override
            public void accept(Object option) {
                if (option != NotifyDescriptor.YES_OPTION) {
                    return;
                }
                for (Planet p : planets) {
                    SolarSystem.get().remove(p);
                    UISession.current().publish(new LogLine(Bundle.MSG_Removed(p.getName())));
                }
            }
        });
    }
}
