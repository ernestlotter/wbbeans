package org.webbeans.scene3d;

import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.webbeans.actions.AbstractAction;
import org.webbeans.actions.Action;
import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionPresenters;
import org.webbeans.actions.Actions;
import org.webbeans.actions.Separator;
import org.webbeans.actions.Toolbar;
import org.webbeans.core.i18n.Bundles;
import org.webbeans.ui.Resource;
import org.webbeans.ui.components.Layout;
import org.webbeans.windows.TopComponent;

/**
 * A window around one {@link Scene3D}: a toolbar (fit, reset view, grid, save PNG, plus the actions registered
 * under {@code Scene3D/Actions}, which receive the scene in their context) and the viewport filling the rest.
 * The scene is in the window's lookup; subclasses typically add the picked node's user data too.
 */
public class Scene3DTopComponent extends TopComponent {

    /** Registry folder of extra toolbar actions; context actions over {@link Scene3D} work. */
    public static final String ACTIONS_FOLDER = "Scene3D/Actions";

    private final Scene3D scene;
    private final Toolbar toolbar = new Toolbar("scene3d");
    private CameraState home;

    public Scene3DTopComponent(Scene3D scene) {
        this.scene = scene;
        setLayout(new Layout.Border());
        associateLookup(Lookups.singleton(scene));
        add(toolbar, Layout.Border.NORTH);
        add(scene, Layout.Border.CENTER);
    }

    public Scene3D getScene() {
        return scene;
    }

    public Toolbar getToolbar() {
        return toolbar;
    }

    /** The pose "reset view" returns to; defaults to the camera's pose when the window first opened. */
    public void setHomeView(CameraState s) {
        home = s;
    }

    @Override
    protected void attached() {
        super.attached();
        if (home == null) {
            home = scene.getCameraState();
        }
        if (toolbar.getChildCount() == 0) {
            buildToolbar();
        }
    }

    private void buildToolbar() {
        Lookup ctx = getLookup();
        toolbar.add(ActionPresenters.toolbarButton(simple("CTL_Fit", "org/webbeans/scene3d/client/fit.svg", new Runnable() {
            @Override
            public void run() {
                scene.fit();
            }
        }), ctx));
        toolbar.add(ActionPresenters.toolbarButton(simple("CTL_ResetView", "org/webbeans/scene3d/client/home.svg", new Runnable() {
            @Override
            public void run() {
                if (home != null) {
                    scene.flyTo(home, 600);
                }
            }
        }), ctx));
        toolbar.add(ActionPresenters.toolbarButton(simple("CTL_ToggleGrid", "org/webbeans/scene3d/client/grid.svg", new Runnable() {
            @Override
            public void run() {
                scene.setGrid(!Boolean.TRUE.equals(scene.get(Scene3D.GRID)));
            }
        }), ctx));
        toolbar.add(ActionPresenters.toolbarButton(simple("CTL_Snapshot", "org/webbeans/scene3d/client/snapshot.svg", new Runnable() {
            @Override
            public void run() {
                scene.download(fileName() + ".png", 0, 0);
            }
        }), ctx));
        boolean first = true;
        for (Action a : Actions.forPath(ACTIONS_FOLDER)) {
            if (a == null) {
                continue;
            }
            if (first) {
                toolbar.add(new Separator());
                first = false;
            }
            toolbar.add(ActionPresenters.toolbarButton(a, ctx));
        }
    }

    private String fileName() {
        String base = getDisplayName();
        return base == null || base.isEmpty() ? "scene" : base.replaceAll("[^A-Za-z0-9_-]+", "_");
    }

    private static Action simple(String key, String icon, final Runnable r) {
        AbstractAction a = new AbstractAction(Bundles.getMessage(Scene3DTopComponent.class, key), Resource.of(icon)) {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        };
        a.putValue(Action.SHORT_DESCRIPTION, Bundles.getMessage(Scene3DTopComponent.class, key));
        return a;
    }
}
