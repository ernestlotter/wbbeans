package org.webbeans.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.webbeans.actions.ActionMap;
import org.webbeans.core.i18n.Messages;
import org.webbeans.explorer.ExplorerManager;
import org.webbeans.explorer.ExplorerUtils;
import org.webbeans.explorer.TreeView;
import org.webbeans.nodes.AbstractNode;
import org.webbeans.scene3d.AmbientLight;
import org.webbeans.scene3d.Animation;
import org.webbeans.scene3d.Axis;
import org.webbeans.scene3d.DragConstraint;
import org.webbeans.scene3d.DragEvent;
import org.webbeans.scene3d.Geometry;
import org.webbeans.scene3d.Group;
import org.webbeans.scene3d.HoverEffect;
import org.webbeans.scene3d.Label3D;
import org.webbeans.scene3d.Line;
import org.webbeans.scene3d.LineMaterial;
import org.webbeans.scene3d.Material;
import org.webbeans.scene3d.Mesh;
import org.webbeans.scene3d.Node3D;
import org.webbeans.scene3d.PickEvent;
import org.webbeans.scene3d.PointLight;
import org.webbeans.scene3d.Scene3D;
import org.webbeans.scene3d.Scene3DChildren;
import org.webbeans.scene3d.Scene3DTopComponent;
import org.webbeans.scene3d.SceneBinder;
import org.webbeans.scene3d.Vector3;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Size;
import org.webbeans.ui.UISession;
import org.webbeans.ui.components.Layout;
import org.webbeans.windows.StatusDisplayer;
import org.webbeans.windows.TopComponent;

/**
 * The solar system in 3D: a glowing sun, one orbiting, spinning sphere per planet bound to the model through a
 * {@link SceneBinder} (adding or renaming a planet in the explorer updates the scene), orbit lines, labels, a
 * draggable probe constrained to the ecliptic plane with snapping and server-side validation, picking that
 * selects in the scene and in the outline tree on the right (whose nodes expose position and scale as editable
 * properties), and the standard viewport toolbar plus the actions registered under {@code Scene3D/Actions}.
 */
@TopComponent.Registration(mode = "editor", openAtStartup = false, position = 70)
@TopComponent.Description(preferredID = "SolarSystem3DTC", iconBase = "org/webbeans/demo/icons/cube.svg")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenSolarSystem3D", position = 320)
@Messages({"CTL_SolarSystem3DTC=Solar system 3D", "CTL_OpenSolarSystem3D=Solar system 3D",
    "LBL_Sun=Sun", "LBL_Probe=Probe", "MSG_Picked3D=Picked {0} at ({1}, {2}, {3})", "MSG_PickedNothing=Nothing picked",
    "MSG_ProbeMoved=Probe moved to ({0}, {1})", "MSG_ProbeRejected=The probe cannot leave the system", "MSG_Flew=Flying to {0}"})
public final class SolarSystem3DTopComponent extends Scene3DTopComponent implements SolarSystem.Listener, ExplorerManager.Provider {

    private static final Material SUN = Material.standard(0xffcc33).setEmissive(0xff9900, 1.2).setRoughness(1);
    private static final Material ROCKY = Material.standard(0xb0875f).setRoughness(0.9);
    private static final Material GAS = Material.standard(0xd9822b).setRoughness(0.7);
    private static final Material ICE = Material.standard(0x5b6fd6).setRoughness(0.5).setMetalness(0.2);
    private static final Material DWARF = Material.standard(0x8a63c9).setRoughness(0.95);
    private static final Material PROBE = Material.standard(0xe4e9ee).setMetalness(0.8).setRoughness(0.3);
    private static final LineMaterial ORBIT = new LineMaterial(0x6b7785);

    private final ExplorerManager em = new ExplorerManager();
    private final ActionMap map = new ActionMap();
    private final TreeView tree = new TreeView();
    private final Group planets = new Group("planets");
    private final SceneBinder<Planet> binder;
    private final java.util.Map<Group, Double> orbitRates = new java.util.HashMap<Group, Double>();
    private final Group probe;

    public SolarSystem3DTopComponent() {
        super(new Scene3D());
        setDisplayName(Bundle.CTL_SolarSystem3DTC());
        final Scene3D scene = getScene();
        scene.setTestId("scene-solar");
        scene.setGrid(40, 40).setHoverEffect(HoverEffect.HIGHLIGHT).setBackground(0x0b1020);
        scene.getCamera().setPosition(0, 18, 26).lookAt(Vector3.ZERO);
        scene.getControls().setDistanceLimits(4, 120).setPolarLimits(0.05, Math.PI / 2 + 0.3);

        Group root = scene.getRoot();
        root.add(new AmbientLight(0xffffff, 0.35), new PointLight(0xfff2cc, 1.6));
        Mesh sun = root.addChild(new Mesh(Bundle.LBL_Sun(), Geometry.sphere(1.8, 48, 24), SUN));
        sun.setUserData(Bundle.LBL_Sun());
        sun.animate(Animation.rotate(Axis.Y, 0.15));
        root.addChild(new Label3D(Bundle.LBL_Sun())).setPosition(0, 2.6, 0);
        root.add(planets);
        // the probe is a draggable group: picking its body starts a drag of the whole group (body + label)
        probe = root.addChild(new Group(Bundle.LBL_Probe()));
        probe.setPosition(6, 0, 6).setDraggable(DragConstraint.plane(Axis.Y, 0).snap(0.5)).setUserData(Bundle.LBL_Probe());
        probe.addChild(new Mesh(Bundle.LBL_Probe(), Geometry.box(0.6, 0.3, 0.6), PROBE)).setUserData(Bundle.LBL_Probe()).setCastShadow(true);
        probe.addChild(new Label3D(Bundle.LBL_Probe())).setPosition(0, 0.7, 0);

        binder = new SceneBinder<Planet>(planets, new Function<Planet, Object>() {
            @Override
            public Object apply(Planet p) {
                return p;
            }
        }, new Function<Planet, Node3D>() {
            @Override
            public Node3D apply(Planet p) {
                return createPlanet(p);
            }
        }, new BiConsumer<Planet, Node3D>() {
            @Override
            public void accept(Planet p, Node3D n) {
                updatePlanet(p, (Group) n);
            }
        });

        // explorer tree over the scene graph, synced with picks
        ExplorerUtils.activateActions(em, map, true);
        associateLookup(new ProxyLookup(Lookups.singleton(scene), ExplorerUtils.createLookup(em, map)));
        em.setRootContext(new AbstractNode(new Scene3DChildren(root)) {
            {
                setName(Bundle.CTL_SolarSystem3DTC());
                setIconBaseWithExtension("org/webbeans/demo/icons/cube.svg");
            }
        });
        tree.setExplorerManager(em);
        tree.setRootVisible(true);
        tree.setTestId("scene-tree");
        tree.setWidth(Size.px(230));
        add(tree, Layout.Border.EAST);
        Scene3DChildren.bindSelection(em, scene);

        scene.addListener(Scene3D.PICK, new Listener<PickEvent>() {
            @Override
            public void onEvent(PickEvent e) {
                Node3D n = e.getNode();
                String msg;
                if (n == null) {
                    msg = Bundle.MSG_PickedNothing();
                } else {
                    Object data = n.getUserData();
                    String name = data instanceof Planet ? ((Planet) data).getName() : data != null ? data.toString() : n.getName();
                    Vector3 p = e.getPoint();
                    msg = Bundle.MSG_Picked3D(name, fmt(p.x), fmt(p.y), fmt(p.z));
                    if (e.isDoubleClick()) {
                        scene.fit(n);
                        msg = Bundle.MSG_Flew(name);
                    }
                }
                StatusDisplayer.getDefault().setStatusText(msg);
                UISession.current().publish(new LogLine(msg));
            }
        });
        scene.addListener(Scene3D.DRAG, new Listener<DragEvent>() {
            @Override
            public void onEvent(DragEvent e) {
                if (e.getPhase() != DragEvent.Phase.END) {
                    return;
                }
                Vector3 p = e.getPosition();
                if (Math.sqrt(p.x * p.x + p.z * p.z) > 20) {
                    e.reject();
                    StatusDisplayer.getDefault().setStatusText(Bundle.MSG_ProbeRejected());
                } else {
                    String msg = Bundle.MSG_ProbeMoved(fmt(p.x), fmt(p.z));
                    StatusDisplayer.getDefault().setStatusText(msg);
                    UISession.current().publish(new LogLine(msg));
                }
            }
        });
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    static double radiusOf(Planet p) {
        switch (p.getType()) {
            case GAS_GIANT:
                return 0.95;
            case ICE_GIANT:
                return 0.7;
            case DWARF:
                return 0.2;
            default:
                return 0.38;
        }
    }

    static Material materialOf(Planet p) {
        switch (p.getType()) {
            case GAS_GIANT:
                return GAS;
            case ICE_GIANT:
                return ICE;
            case DWARF:
                return DWARF;
            default:
                return ROCKY;
        }
    }

    /** A pivot group (revolves around the sun) holding the orbit line, the spinning sphere and its label. */
    private Node3D createPlanet(Planet p) {
        Group pivot = new Group(p.getName());
        Mesh body = pivot.addChild(new Mesh(p.getName(), Geometry.sphere(radiusOf(p), 32, 16), materialOf(p)));
        body.setUserData(p);
        body.setCastShadow(true);
        body.animate(Animation.rotate(Axis.Y, 0.8));
        pivot.addChild(new Line(Line.circle(1, 72), ORBIT)).setName("orbit");
        pivot.addChild(new Label3D(p.getName())).setName("label");
        return pivot;
    }

    private void updatePlanet(Planet p, Group pivot) {
        List<Planet> all = SolarSystem.get().getPlanets();
        int i = Math.max(0, all.indexOf(p));
        double orbit = 4.5 + i * 2.2;
        Mesh body = (Mesh) pivot.getChildren().get(0);
        Line line = (Line) pivot.getChildren().get(1);
        Label3D label = (Label3D) pivot.getChildren().get(2);
        pivot.setName(p.getName());
        body.setName(p.getName()).setUserData(p).setPosition(orbit, 0, 0);
        body.setGeometry(Geometry.sphere(radiusOf(p), 32, 16));
        body.setMaterial(materialOf(p));
        line.setScale(orbit);
        label.setText(p.getName()).setPosition(orbit, radiusOf(p) + 0.6, 0);
        // one revolution per (12 + 6i) seconds, in the plane of the ecliptic; restarted only when the period changes
        double rate = 2 * Math.PI / (12 + 6 * i);
        Double current = orbitRates.get(pivot);
        if (current == null || current != rate) {
            pivot.stopAnimations();
            pivot.animate(Animation.rotate(Axis.Y, rate));
            orbitRates.put(pivot, rate);
        }
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return em;
    }

    @Override
    protected void componentOpened() {
        SolarSystem.get().addListener(this);
        binder.setItems(SolarSystem.get().getPlanets());
    }

    @Override
    protected void componentClosed() {
        SolarSystem.get().removeListener(this);
    }

    @Override
    public void changed() {
        if (isOpened()) {
            binder.setItems(SolarSystem.get().getPlanets());
        }
    }

    @Override
    public void planetChanged(Planet p, String property) {
        if (isOpened() && !"explored".equals(property)) {
            binder.refresh(p);
        }
    }

    /** All planet bodies (for actions). */
    public List<Mesh> getPlanetBodies() {
        List<Mesh> out = new ArrayList<Mesh>();
        for (Node3D n : planets.getChildren()) {
            out.add((Mesh) ((Group) n).getChildren().get(0));
        }
        return out;
    }
}
