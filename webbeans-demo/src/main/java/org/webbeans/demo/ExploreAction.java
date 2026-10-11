package org.webbeans.demo;

import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionReferences;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.core.i18n.Messages;
import org.webbeans.notifications.NotificationDisplayer;
import org.webbeans.ui.UISession;

/**
 * A single-context action: enabled when exactly one {@link Planet} is selected (the constructor argument is
 * the context type). Marks the planet explored and posts a notification.
 */
@ActionID(category = "Planets", id = "org.webbeans.demo.ExploreAction")
@ActionRegistration(displayName = "#CTL_Explore", iconBase = "org/webbeans/demo/icons/explore.svg")
@ActionReferences({
    @ActionReference(path = "Menu/Planets", position = 100),
    @ActionReference(path = "Toolbars/Planets", position = 100),
    @ActionReference(path = PlanetNode.ACTIONS_FOLDER, position = 100),
    @ActionReference(path = "Shortcuts", name = "D-E")})
@Messages({"CTL_Explore=Explore planet", "MSG_Explored={0} is now explored", "MSG_AlreadyExplored={0} was explored already"})
public final class ExploreAction implements ActionListener {

    private final Planet planet;

    public ExploreAction(Planet planet) {
        this.planet = planet;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        boolean was = planet.isExplored();
        if (!was) {
            planet.setExplored(true);
            SolarSystem.get().planetChanged(planet, "explored");
        }
        UISession.current().publish(new LogLine(was ? Bundle.MSG_AlreadyExplored(planet.getName()) : Bundle.MSG_Explored(planet.getName())));
        NotificationDisplayer.getDefault().notify(was ? Bundle.MSG_AlreadyExplored(planet.getName()) : Bundle.MSG_Explored(planet.getName()),
                null, NotificationDisplayer.Category.INFO);
    }
}
