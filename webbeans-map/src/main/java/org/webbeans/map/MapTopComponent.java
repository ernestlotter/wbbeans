package org.webbeans.map;

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
 * A window around one {@link MapView}: a toolbar (zoom in/out, fit the overlays, the draw modes, plus the
 * actions registered under {@code Map/Actions}, which receive the map in their context) and the map filling the
 * rest. The map is in the window's lookup.
 */
public class MapTopComponent extends TopComponent {

    public static final String ACTIONS_FOLDER = "Map/Actions";

    private final MapView map;
    private final Toolbar toolbar = new Toolbar("map");
    private boolean drawTools = true;

    public MapTopComponent(MapView map) {
        this.map = map;
        setLayout(new Layout.Border());
        associateLookup(Lookups.singleton(map));
        add(toolbar, Layout.Border.NORTH);
        add(map, Layout.Border.CENTER);
    }

    public MapView getMap() {
        return map;
    }

    public Toolbar getToolbar() {
        return toolbar;
    }

    /** Whether the toolbar offers the draw modes (default true). */
    public MapTopComponent setDrawTools(boolean b) {
        drawTools = b;
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
        toolbar.add(ActionPresenters.toolbarButton(simple("CTL_ZoomIn", "org/webbeans/map/client/zoom-in.svg", new Runnable() {
            @Override
            public void run() {
                map.setZoom(Math.floor(map.getZoom()) + 1);
            }
        }), ctx));
        toolbar.add(ActionPresenters.toolbarButton(simple("CTL_ZoomOut", "org/webbeans/map/client/zoom-out.svg", new Runnable() {
            @Override
            public void run() {
                map.setZoom(Math.ceil(map.getZoom()) - 1);
            }
        }), ctx));
        toolbar.add(ActionPresenters.toolbarButton(simple("CTL_FitOverlays", "org/webbeans/map/client/fit.svg", new Runnable() {
            @Override
            public void run() {
                map.fitOverlays(40);
            }
        }), ctx));
        if (drawTools) {
            toolbar.add(new Separator());
            for (final MapView.DrawMode m : new MapView.DrawMode[] {MapView.DrawMode.MARKER, MapView.DrawMode.LINE, MapView.DrawMode.POLYGON,
                MapView.DrawMode.RECTANGLE, MapView.DrawMode.CIRCLE}) {
                String name = m.name().toLowerCase(java.util.Locale.ROOT);
                toolbar.add(ActionPresenters.toolbarButton(simple("CTL_Draw_" + name, "org/webbeans/map/client/draw-" + name + ".svg", new Runnable() {
                    @Override
                    public void run() {
                        map.setDrawMode(map.getDrawMode() == m ? MapView.DrawMode.NONE : m);
                    }
                }), ctx));
            }
        }
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

    private static Action simple(String key, String icon, final Runnable r) {
        AbstractAction a = new AbstractAction(Bundles.getMessage(MapTopComponent.class, key), Resource.of(icon)) {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        };
        a.putValue(Action.SHORT_DESCRIPTION, Bundles.getMessage(MapTopComponent.class, key));
        return a;
    }
}
