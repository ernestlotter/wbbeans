package org.webbeans.graph;

import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
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
 * A window around one {@link GraphScene}: a toolbar (zoom in/out, fit, arrange with the window's layout, save
 * as SVG/PNG, plus the actions registered under {@code Graph/Actions}, which receive the scene in their
 * context) and the scene filling the rest. The scene and its selection (objects and widgets) are in the
 * window's lookup, so context actions, the Properties window and other windows see what is selected.
 */
public class GraphTopComponent extends TopComponent {

    /** Registry folder of extra toolbar actions; context actions over {@link GraphScene} work. */
    public static final String ACTIONS_FOLDER = "Graph/Actions";

    private final GraphScene<?, ?> scene;
    private final Toolbar toolbar = new Toolbar("graph");
    private GraphLayout layout = new TreeGraphLayout();

    public GraphTopComponent(GraphScene<?, ?> scene) {
        this.scene = scene;
        setLayout(new Layout.Border());
        associateLookup(new ProxyLookup(scene.getSelectionLookup(), Lookups.singleton(scene)));
        setActivatedNodesOwner(true);
        add(toolbar, Layout.Border.NORTH);
        add(scene, Layout.Border.CENTER);
    }

    public GraphScene<?, ?> getScene() {
        return scene;
    }

    public Toolbar getToolbar() {
        return toolbar;
    }

    /** The layout the toolbar's Arrange button runs (default: {@link TreeGraphLayout}). */
    public GraphTopComponent setGraphLayout(GraphLayout l) {
        layout = l;
        return this;
    }

    @Override
    protected void attached() {
        super.attached();
        if (toolbar.getChildCount() == 0) {
            buildToolbar();
        }
    }

    private void buildToolbar() {
        Lookup ctx = getLookup();
        toolbar.add(ActionPresenters.toolbarButton(simple("CTL_ZoomIn", "org/webbeans/graph/client/zoom-in.svg", new Runnable() {
            @Override
            public void run() {
                scene.call("view", "zoomBy", 1.25);
            }
        }), ctx));
        toolbar.add(ActionPresenters.toolbarButton(simple("CTL_ZoomOut", "org/webbeans/graph/client/zoom-out.svg", new Runnable() {
            @Override
            public void run() {
                scene.call("view", "zoomBy", 0.8);
            }
        }), ctx));
        toolbar.add(ActionPresenters.toolbarButton(simple("CTL_Fit", "org/webbeans/graph/client/fit.svg", new Runnable() {
            @Override
            public void run() {
                scene.fitToView();
            }
        }), ctx));
        toolbar.add(ActionPresenters.toolbarButton(simple("CTL_Layout", "org/webbeans/graph/client/layout.svg", new Runnable() {
            @Override
            public void run() {
                if (layout != null) {
                    scene.layout(layout, true);
                }
            }
        }), ctx));
        toolbar.add(new Separator());
        toolbar.add(ActionPresenters.toolbarButton(simple("CTL_SaveSvg", "org/webbeans/graph/client/save-svg.svg", new Runnable() {
            @Override
            public void run() {
                scene.download("svg", fileName() + ".svg");
            }
        }), ctx));
        toolbar.add(ActionPresenters.toolbarButton(simple("CTL_SavePng", "org/webbeans/graph/client/save-png.svg", new Runnable() {
            @Override
            public void run() {
                scene.download("png", fileName() + ".png");
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
        return base == null || base.isEmpty() ? "graph" : base.replaceAll("[^A-Za-z0-9_-]+", "_");
    }

    private static Action simple(String key, String icon, final Runnable r) {
        AbstractAction a = new AbstractAction(Bundles.getMessage(GraphTopComponent.class, key), Resource.of(icon)) {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        };
        a.putValue(Action.SHORT_DESCRIPTION, Bundles.getMessage(GraphTopComponent.class, key));
        return a;
    }
}
