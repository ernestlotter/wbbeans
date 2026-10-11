package org.webbeans.workbench.viewer3d;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.openide.util.lookup.ProxyLookup;
import org.webbeans.actions.ActionMap;
import org.webbeans.core.i18n.Messages;
import org.webbeans.explorer.ExplorerManager;
import org.webbeans.explorer.ExplorerUtils;
import org.webbeans.nodes.Node;
import org.webbeans.scene3d.AmbientLight;
import org.webbeans.scene3d.Animation;
import org.webbeans.scene3d.Axis;
import org.webbeans.scene3d.DirectionalLight;
import org.webbeans.scene3d.Group;
import org.webbeans.scene3d.HoverEffect;
import org.webbeans.scene3d.Label3D;
import org.webbeans.scene3d.Material;
import org.webbeans.scene3d.Mesh;
import org.webbeans.scene3d.Node3D;
import org.webbeans.scene3d.PickEvent;
import org.webbeans.scene3d.Quaternion;
import org.webbeans.scene3d.Scene3D;
import org.webbeans.scene3d.Scene3DTopComponent;
import org.webbeans.scene3d.SceneBinder;
import org.webbeans.scene3d.Vector3;
import org.webbeans.ui.Listener;
import org.webbeans.windows.StatusDisplayer;
import org.webbeans.windows.TopComponent;
import org.webbeans.workbench.core.api.Log;

/**
 * The 3D Viewer: the turbine from {@link PartsModel} through a {@link SceneBinder} (blades under a spinning rotor
 * group, sensors colour-coded by temperature, labels), picking that selects the part in the model (and so in
 * the Parts tree and the Properties window), and the standard viewport toolbar plus {@code Scene3D/Actions}.
 * The window's lookup holds the scene, the selected part's node and an {@link ExplorerManager} over the same
 * root node as the Parts window.
 */
@TopComponent.Registration(mode = "editor", openAtStartup = true, position = 300)
@TopComponent.Description(preferredID = "ViewerTC", iconBase = "org/webbeans/workbench/viewer3d/icons/turbine.svg", helpId = "viewer")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenViewer", position = 300)
@Messages({"CTL_ViewerModule=Workbench 3D Viewer", "CTL_ViewerTC=3D Viewer", "CTL_OpenViewer=3D Viewer", "MSG_Picked=Picked {0}",
    "MSG_PickedNothing=Nothing selected", "LBL_Hot={0}: {1} °C"})
public final class ViewerTopComponent extends Scene3DTopComponent implements ExplorerManager.Provider, PartsModel.Listener {

    private static final Material HOT = Material.standard(0xb43b3b).setEmissive(0x5a0000, 0.8);

    private final ExplorerManager em = new ExplorerManager();
    private final ActionMap map = new ActionMap();
    private final Group rotor = new Group("rotor");
    private final Group fixed = new Group("fixed");
    private SceneBinder<Part> binder;
    private PartsModel model;
    private boolean syncing;

    public ViewerTopComponent() {
        super(new Scene3D());
        setDisplayName(Bundle.CTL_ViewerTC());
        final Scene3D scene = getScene();
        scene.setTestId("viewer");
        scene.setGrid(20, 20).setShadows(true).setHoverEffect(HoverEffect.HIGHLIGHT);
        scene.getCamera().setPosition(7, 5, 9).lookAt(0, 0, -1);
        scene.getControls().setDistanceLimits(3, 60).setPolarLimits(0.05, Math.PI / 2 + 0.2);
        DirectionalLight sun = new DirectionalLight(0xffffff, 0.9);
        sun.setShadowSize(12);
        sun.setPosition(8, 14, 10).setCastShadow(true);
        scene.getRoot().add(new AmbientLight(0xffffff, 0.45), sun);
        Mesh floor = new Mesh("floor", org.webbeans.scene3d.Geometry.plane(30, 30), Material.standard(0x232a33).setRoughness(1));
        floor.setRotationEuler(-Math.PI / 2, 0, 0).setPosition(0, -4, 0).setReceiveShadow(true).setPickable(false);
        scene.getRoot().add(floor, fixed, rotor);
        ExplorerUtils.activateActions(em, map, true);
        associateLookup(new ProxyLookup(org.openide.util.lookup.Lookups.singleton(scene), ExplorerUtils.createLookup(em, map)));
        scene.addListener(Scene3D.PICK, new Listener<PickEvent>() {
            @Override
            public void onEvent(PickEvent e) {
                Object data = e.getUserData();
                Part p = data instanceof Part ? (Part) data : null;
                if (model != null) {
                    model.setSelected(p);
                }
                StatusDisplayer.getDefault().setStatusText(p == null ? Bundle.MSG_PickedNothing() : Bundle.MSG_Picked(p.getName()));
                if (p != null && e.isDoubleClick()) {
                    scene.fit(e.getNode());
                }
            }
        });
        em.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (ExplorerManager.PROP_SELECTED_NODES.equals(evt.getPropertyName()) && !syncing && model != null) {
                    Node[] sel = em.getSelectedNodes();
                    model.setSelected(sel.length > 0 ? sel[0].getLookup().lookup(Part.class) : null);
                }
            }
        });
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return em;
    }

    @Override
    protected void localeChanged() {
        setDisplayName(Bundle.CTL_ViewerTC());
    }

    @Override
    protected void componentOpened() {
        model = PartsModel.get();
        if (em.getRootContext() == null) {
            em.setRootContext(new PartsRootNode(model));
        }
        if (binder == null) {
            binder = new SceneBinder<Part>(fixed, new Function<Part, Object>() {
                @Override
                public Object apply(Part p) {
                    return p.getId();
                }
            }, new Function<Part, Node3D>() {
                @Override
                public Node3D apply(Part p) {
                    return create(p);
                }
            }, new BiConsumer<Part, Node3D>() {
                @Override
                public void accept(Part p, Node3D n) {
                    update(p, n);
                }
            });
        }
        model.addListener(this);
        partsChanged();
        selectionChanged(model.getSelected());
    }

    @Override
    protected void componentClosed() {
        if (model != null) {
            model.removeListener(this);
        }
    }

    private Node3D create(Part p) {
        Group g = new Group(p.getName());
        Mesh body;
        switch (p.getKind()) {
            case BLADE:
                body = new Mesh(p.getName(), Geometries.BLADE, Material.standard(p.getColor()).setSide(Material.Side.DOUBLE).setRoughness(0.4).setMetalness(0.6));
                break;
            case HUB:
                body = new Mesh(p.getName(), Geometries.HUB, Material.standard(p.getColor()).setMetalness(0.7).setRoughness(0.3));
                body.setRotationEuler(Math.PI / 2, 0, 0);
                break;
            case SHAFT:
                body = new Mesh(p.getName(), Geometries.SHAFT, Material.standard(p.getColor()).setMetalness(0.8).setRoughness(0.25));
                body.setRotationEuler(Math.PI / 2, 0, 0);
                break;
            case HOUSING:
                body = new Mesh(p.getName(), Geometries.HOUSING, Material.standard(p.getColor()).setRoughness(0.6));
                break;
            default:
                body = new Mesh(p.getName(), Geometries.SENSOR, Material.standard(p.getColor()).setEmissive(p.getColor(), 0.3));
        }
        body.setCastShadow(true).setUserData(p);
        g.addChild(body);                                   // child 0: the body
        if (p.getKind() == Part.Kind.SENSOR) {
            g.addChild(new Label3D(p.getName())).setPosition(0, 0.5, 0);   // child 1: the live label
        }
        g.setUserData(p);
        return g;
    }

    private void update(Part p, Node3D n) {
        Group g = (Group) n;
        Mesh body = (Mesh) g.getChildren().get(0);
        g.setName(p.getName());
        body.setName(p.getName());
        if (p.getKind() == Part.Kind.BLADE) {
            // blades live under the rotor (so the whole rotor can spin as one animation)
            if (g.getParent() != rotor) {
                rotor.add(g);
            }
            g.setPosition(Vector3.ZERO).setRotation(Quaternion.fromAxisAngle(Vector3.Z, p.getAngle()));
        } else {
            g.setPosition(p.getPosition());
        }
        if (p.getKind() == Part.Kind.SENSOR) {
            body.setMaterial(p.isOk() ? Material.standard(p.getColor()).setEmissive(p.getColor(), 0.3) : HOT);
            ((Label3D) g.getChildren().get(1)).setText(Bundle.LBL_Hot(p.getName(), String.valueOf(p.getTemperature())));
        } else if (body.getMaterial().getColor() != p.getColor()) {
            body.getMaterial().setColor(p.getColor());
        }
    }

    // ---- model listener (session executor) -------------------------------------------------------------------------

    @Override
    public void partsChanged() {
        binder.setItems(model.getParts());
        if (model.isSpinning()) {
            if (rotor.getAnimations().isEmpty()) {
                rotor.animate(Animation.rotate(Axis.Z, 1.2));
            }
        } else {
            rotor.stopAnimations();
        }
    }

    @Override
    public void partChanged(Part p) {
        if (binder != null) {
            binder.refresh(p);
        }
    }

    @Override
    public void selectionChanged(Part selected) {
        Node3D n = selected == null ? null : binder.nodeFor(selected);
        getScene().setSelection(n == null ? java.util.Collections.<Node3D>emptyList() : java.util.Collections.singletonList(n));
        Node node = selected == null || em.getRootContext() == null ? null : PartsRootNode.find(em.getRootContext(), selected);
        syncing = true;
        try {
            em.setSelectedNodes(node == null ? new Node[0] : new Node[] {node});
        } catch (PropertyVetoException e) {
            // keep the old selection
        } finally {
            syncing = false;
        }
        if (selected != null) {
            Log.info("viewer", Bundle.MSG_Picked(selected.getName()));
        }
    }
}
