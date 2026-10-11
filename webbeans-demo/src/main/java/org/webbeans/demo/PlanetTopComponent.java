package org.webbeans.demo;

import java.util.Map;

import org.openide.util.lookup.Lookups;
import org.webbeans.core.i18n.Messages;
import org.webbeans.explorer.PropertySheet;
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.Listener;
import org.webbeans.ui.UISession;
import org.webbeans.ui.components.Button;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;
import org.webbeans.windows.TopComponent;
import org.webbeans.windows.WindowManager;

/**
 * One window per planet, opened from the Planets node (multi-instance: ids {@code PlanetTC#1}, {@code #2}...).
 * The planet's node is in the lookup, so the Properties window follows it; {@link Factory} recreates the window
 * from {@link #writeState} at the next login.
 */
@TopComponent.Description(preferredID = "PlanetTC", iconBase = "org/webbeans/demo/icons/planet.svg", factory = PlanetTopComponent.Factory.class)
@Messages({"LBL_Gravity=Gravity: {0} m/s²", "BTN_Mark=Mark as modified", "MSG_PlanetOpened=Planet {0} opened", "LBL_PlanetSheet=Edit here or in the Properties window:"})
public final class PlanetTopComponent extends TopComponent {

    private final Planet planet;
    private final PlanetNode node;
    private final Label gravity = new Label();

    public PlanetTopComponent(Planet planet) {
        this.planet = planet;
        this.node = new PlanetNode(planet);
        setDisplayName(planet.getName());
        setLayout(new Layout.Border());
        associateLookup(Lookups.fixed(planet, node));
        Panel top = new Panel(new Layout.Flow(Layout.Orientation.VERTICAL, 8));
        top.setPadding(12);
        gravity.setTestId("gravity");
        refresh();
        top.add(gravity);
        Button mark = new Button(Bundle.BTN_Mark());
        mark.setTestId("mark");
        mark.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                setModified(!isModified());
                setHtmlDisplayName(isModified() ? "<b>" + PlanetTopComponent.this.planet.getName() + "</b>" : null);
            }
        });
        top.add(mark);
        top.add(new Label(Bundle.LBL_PlanetSheet()));
        add(top, Layout.Border.NORTH);
        PropertySheet sheet = new PropertySheet();
        sheet.setNodes(node);
        sheet.setTestId("planet-sheet");
        add(sheet, Layout.Border.CENTER);
        node.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            @Override
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                refresh();
                setDisplayName(PlanetTopComponent.this.planet.getName());
            }
        });
    }

    private void refresh() {
        gravity.setText(Bundle.LBL_Gravity(String.valueOf(planet.getGravity())));
    }

    /** Finds the open window for the planet and activates it, or opens a new one. */
    public static void openFor(WindowManager wm, Planet p) {
        for (TopComponent tc : wm.getOpened()) {
            if (tc instanceof PlanetTopComponent && ((PlanetTopComponent) tc).planet == p) {
                tc.requestActive();
                return;
            }
        }
        PlanetTopComponent tc = new PlanetTopComponent(p);
        tc.open();
        tc.requestActive();
        UISession.current().publish(new LogLine(Bundle.MSG_PlanetOpened(p.getName())));
    }

    @Override
    protected void writeState(Map<String, Object> out) {
        out.put("planet", planet.getName());
    }

    /** Recreates the window from its saved state after a reload or at the next login. */
    public static final class Factory implements TopComponent.Factory {
        @Override
        public TopComponent create(Map<String, Object> state) {
            Planet p = SolarSystem.get().find(String.valueOf(state.get("planet")));
            return p == null ? null : new PlanetTopComponent(p);
        }
    }
}
