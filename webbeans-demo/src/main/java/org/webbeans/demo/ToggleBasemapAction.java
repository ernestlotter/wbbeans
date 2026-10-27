package org.webbeans.demo;

import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.core.i18n.Messages;
import org.webbeans.map.MapTopComponent;
import org.webbeans.map.MapView;
import org.webbeans.map.TileSource;

/** A context action over {@link MapView} (the map window puts its map in the lookup): OpenStreetMap tiles or none. */
@ActionID(category = "Map", id = "org.webbeans.demo.ToggleBasemapAction")
@ActionRegistration(displayName = "#CTL_ToggleBasemap", iconBase = "org/webbeans/demo/icons/sun.svg")
@ActionReference(path = MapTopComponent.ACTIONS_FOLDER, position = 100)
@Messages({"CTL_ToggleBasemap=Toggle base map (OpenStreetMap / none)"})
public final class ToggleBasemapAction implements ActionListener {

    private final MapView map;

    public ToggleBasemapAction(MapView map) {
        this.map = map;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        map.setTileSource(map.getTileSource() == TileSource.BLANK ? TileSource.OSM : TileSource.BLANK);
    }
}
