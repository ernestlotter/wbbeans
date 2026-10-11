package org.webbeans.demo;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.webbeans.actions.ActionMap;
import org.webbeans.core.i18n.Messages;
import org.webbeans.explorer.ExplorerManager;
import org.webbeans.explorer.ExplorerUtils;
import org.webbeans.explorer.OutlineView;
import org.webbeans.nodes.Node;
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.Listener;
import org.webbeans.ui.UISession;
import org.webbeans.ui.components.Button;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;
import org.webbeans.windows.StatusDisplayer;
import org.webbeans.windows.TopComponent;

/**
 * The explorer window: an {@link OutlineView} over the solar system with editable property columns. The selection
 * goes into the window's lookup ({@link ExplorerUtils#createLookup}), so the Properties window and context-aware
 * actions see it through the global context.
 */
@TopComponent.Registration(mode = "explorer", openAtStartup = true, position = 100)
@TopComponent.Description(preferredID = "PlanetsTC", iconBase = "org/webbeans/demo/icons/planet.svg")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenPlanets", position = 400)
@Messages({"CTL_PlanetsTC=Planets", "CTL_OpenPlanets=Planets", "COL_Planet=Planet", "COL_Gravity=Gravity", "COL_Moons=Moons", "COL_Type=Type",
    "BTN_AddPlanet=Add planet", "MSG_Selected={0} selected", "MSG_Added=Added {0}"})
public final class PlanetsTopComponent extends TopComponent implements ExplorerManager.Provider {

    private final ExplorerManager em = new ExplorerManager();
    private final OutlineView view = new OutlineView(Bundle.COL_Planet());
    private final ActionMap map = new ActionMap();
    private int added;

    public PlanetsTopComponent() {
        setDisplayName(Bundle.CTL_PlanetsTC());
        setLayout(new Layout.Border());
        ExplorerUtils.activateActions(em, map, true); // Edit menu, toolbar and Ctrl+C/X/V reach this explorer
        associateLookup(ExplorerUtils.createLookup(em, map));
        view.setPropertyColumns("gravity", Bundle.COL_Gravity(), "moons", Bundle.COL_Moons(), "type", Bundle.COL_Type());
        view.setColumnWidth("gravity", 70).setColumnWidth("moons", 60).setColumnWidth("type", 90);
        view.setRootVisible(true);
        view.setDragSource(true);
        view.setTestId("planets");
        add(view, Layout.Border.CENTER);
        Panel bar = new Panel(new Layout.Flow(Layout.Orientation.HORIZONTAL, 6));
        bar.setPadding(4);
        Button addBtn = new Button(Bundle.BTN_AddPlanet());
        addBtn.setTestId("add-planet");
        addBtn.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                addPlanet("Planet " + (++added));
            }
        });
        bar.add(addBtn);
        add(bar, Layout.Border.SOUTH);
        em.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (ExplorerManager.PROP_SELECTED_NODES.equals(evt.getPropertyName())) {
                    Node[] sel = em.getSelectedNodes();
                    StatusDisplayer sd = StatusDisplayer.getDefault();
                    if (sd != null) {
                        sd.setStatusText(sel.length == 0 ? "" : Bundle.MSG_Selected(sel.length == 1 ? sel[0].getDisplayName() : String.valueOf(sel.length)));
                    }
                }
            }
        });
    }

    /** Adds a planet with default attributes (the Add button and the New Planet action use it). */
    public static Planet addPlanet(String name) {
        Planet p = new Planet(name, 5.0, 0, Planet.Type.ROCKY, false);
        SolarSystem.get().add(p);
        UISession.current().publish(new LogLine(Bundle.MSG_Added(p.getName())));
        return p;
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return em;
    }

    @Override
    protected void componentOpened() {
        em.setRootContext(new SolarSystemNode(SolarSystem.get()));
    }

}
