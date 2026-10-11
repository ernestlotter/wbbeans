package org.webbeans.workbench.viewer3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.webbeans.nodes.Node;
import org.webbeans.scene3d.Node3D;
import org.webbeans.scene3d.Scene3D;
import org.webbeans.test.HeadlessUI;
import org.webbeans.windows.TopComponent;

/** The viewer and the parts tree over the session's model, picking, selection sync and the simulation push. */
public class ViewerTest {

    @Test
    public void viewerBuildsTheTurbineAndPicksSelectInTreeAndModel() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            ViewerTopComponent viewer = (ViewerTopComponent) h.open("ViewerTC");
            PartsTopComponent parts = (PartsTopComponent) h.open("PartsTC");
            Scene3D scene = viewer.getScene();
            PartsModel model = h.session().getLookup().lookup(PartsModel.class);
            assertEquals(6, model.getBladeCount());
            assertTrue("hub, shaft, housing, 6 blades, 3 sensors as groups", scene.getNodes().size() > 12);
            Part blade = model.find("blade-3");
            Node3D node = scene.findByUserData(blade);
            assertNotNull(node);
            // a pick from the browser selects the part everywhere
            h.inject(scene, "pick", HeadlessUI.map("node", ((org.webbeans.scene3d.Group) node).getChildren().get(0).getId(), "point", java.util.Arrays.asList(0.0, 1.0, 0.0), "button", 0));
            assertSame(blade, model.getSelected());
            assertEquals(1, scene.getSelection().size());
            Node[] sel = parts.getExplorerManager().getSelectedNodes();
            assertEquals(1, sel.length);
            assertSame(blade, sel[0].getLookup().lookup(Part.class));
            // the global context (activated window = parts tree) carries the part and its node
            h.activate(parts);
            assertSame(blade, h.globalContext().lookup(Part.class));
            // the model changes → the scene and the tree follow
            h.run(new Runnable() {
                @Override
                public void run() {
                    PartsModel.get().setBladeCount(8);
                }
            });
            assertEquals(8, model.getBladeCount());
            assertEquals(8, countBlades(scene));
            assertEquals(1 + 8 + 2 + 3, parts.getExplorerManager().getRootContext().getChildren().getNodes().length);
        }
    }

    private static int countBlades(Scene3D scene) {
        int n = 0;
        for (Node3D nd : scene.getNodes()) {
            if (nd.getUserData() instanceof Part && ((Part) nd.getUserData()).getKind() == Part.Kind.BLADE && nd instanceof org.webbeans.scene3d.Group) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void simulationPushesSensorReadingsToTheScene() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            ViewerTopComponent viewer = (ViewerTopComponent) h.open("ViewerTC");
            Part sensor = h.session().getLookup().lookup(PartsModel.class).find("sensor-a");
            double before = sensor.getTemperature();
            // the simulation thread ticks every second and hops onto the session executor; a reading can stay at
            // the 20 °C floor for a tick or two, so poll instead of sleeping once
            for (int i = 0; i < 80 && sensor.getTemperature() == before; i++) {
                Thread.sleep(100);
            }
            assertTrue("temperature changed: " + sensor.getTemperature(), sensor.getTemperature() != before);
            boolean labelUpdated = false;
            for (Map<String, Object> call : h.callsOf(viewer.getScene(), "scene")) {
                if (String.valueOf(call.get("args")).contains("Sensor A: " + sensor.getTemperature())) {
                    labelUpdated = true;
                }
            }
            assertTrue("the label text travelled to the client", labelUpdated);
        }
    }

    @Test
    public void propertyEditsGoThroughTheModel() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            PartsTopComponent parts = (PartsTopComponent) h.open("PartsTC");
            final Part hub = h.session().getLookup().lookup(PartsModel.class).find("hub");
            final Node n = PartsRootNode.find(parts.getExplorerManager().getRootContext(), hub);
            h.run(new Runnable() {
                @Override
                public void run() {
                    try {
                        @SuppressWarnings("unchecked")
                        Node.Property<String> color = (Node.Property<String>) n.getProperty("color");
                        color.setValue("#ff0000");
                        n.setName("Rotor hub");
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                }
            });
            assertEquals(0xff0000, hub.getColor());
            assertEquals("Rotor hub", hub.getName());
        }
    }

    @Test
    public void bladeActionsChangeTheModel() throws Exception {
        try (HeadlessUI h = HeadlessUI.start()) {
            h.open("ViewerTC");
            TopComponent tc = h.wm().findTopComponent("ViewerTC");
            assertNotNull(tc);
            final org.webbeans.actions.Action more = org.webbeans.actions.Actions.forID("Viewer", "org.webbeans.workbench.viewer3d.MoreBladesAction");
            assertNotNull(more);
            h.run(new Runnable() {
                @Override
                public void run() {
                    org.webbeans.actions.Actions.invoke(more, null, h.globalContext());
                }
            });
            assertEquals(7, h.session().getLookup().lookup(PartsModel.class).getBladeCount());
        }
    }
}
