package org.webbeans.demo;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.webbeans.actions.AbstractAction;
import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.Actions;
import org.webbeans.actions.Action;
import org.webbeans.core.i18n.Messages;
import org.webbeans.core.registry.Registration;
import org.webbeans.nodes.AbstractNode;
import org.webbeans.nodes.Children;
import org.webbeans.nodes.Node;
import org.webbeans.nodes.NodeHandleFactory;
import org.webbeans.nodes.Openable;
import org.webbeans.nodes.PropertySupport;
import org.webbeans.nodes.Sheet;
import org.webbeans.ui.UISession;
import org.webbeans.windows.WindowManager;

/**
 * A planet as a node: the planet and an {@link Openable} in the lookup (double-click opens its window), a
 * property sheet with editable properties, rename and delete, and a persistable {@link Handle}.
 */
@Messages({
    "PROP_Gravity=Gravity", "HINT_Gravity=Surface gravity in m/s²",
    "PROP_Moons=Moons", "HINT_Moons=Number of known moons",
    "PROP_Type=Type", "HINT_Type=Planet class",
    "PROP_Explored=Explored", "HINT_Explored=Visited by a probe",
    "PROP_Name=Name", "HINT_PlanetName=The planet's name (rename)",
    "CTL_OpenPlanet=Open", "CTL_ToggleExplored=Toggle explored",
    "MSG_Renamed=Renamed {0} to {1}", "MSG_Deleted=Deleted {0}"
})
public final class PlanetNode extends AbstractNode implements SolarSystem.Listener {

    private final Planet planet;

    public PlanetNode(final Planet planet) {
        super(Children.LEAF, Lookups.fixed(planet, new Openable() {
            @Override
            public void open() {
                PlanetTopComponent.openFor(WindowManager.getDefault(), planet);
            }
        }));
        this.planet = planet;
        setName(planet.getName());
        setIconBase(planet.getType() == Planet.Type.DWARF ? "org/webbeans/demo/icons/dwarf.svg" : "org/webbeans/demo/icons/planet.svg");
        setShortDescription(planet.getName() + " (" + planet.getType() + ")");
        setCanRename(true);
        setCanDestroy(true);
        setCanCut(true);
        // shared action instances: NodeOp.findActions intersects by identity for multi-selections
        setActions(OPEN, TOGGLE_EXPLORED);
        // several nodes may show one planet (the tree, each planet window): the model tells them all
        SolarSystem.get().addListener(this);
    }

    /** The node's own actions, then the registered ones of the {@code Planets/Actions} folder (role-filtered). */
    @Override
    public Action[] getActions(boolean context) {
        java.util.List<Action> all = new java.util.ArrayList<Action>(java.util.Arrays.asList(super.getActions(context)));
        java.util.List<Action> registered = Actions.forPath(ACTIONS_FOLDER);
        if (!registered.isEmpty()) {
            all.add(null);
            all.addAll(registered);
        }
        return all.toArray(new Action[all.size()]);
    }

    @Override
    public void changed() {
    }

    @Override
    public void planetChanged(Planet p, String property) {
        if (p == planet) {
            if ("name".equals(property)) {
                super.setName(p.getName());
            } else {
                firePropertyChange(property, null, null);
            }
        }
    }

    /** Changes a planet attribute through the model so every node of it refreshes. */
    private static void change(Planet p, String property, Runnable mutation) {
        mutation.run();
        SolarSystem.get().planetChanged(p, property);
    }

    /** Registry folder of the popup actions contributed for planet nodes. */
    public static final String ACTIONS_FOLDER = "Planets/Actions";

    /** Opens the window of every planet in the context. */
    static final Action OPEN = new AbstractAction(Bundle.CTL_OpenPlanet()) {
        @Override
        public void actionPerformed(ActionEvent e) {
            for (Planet p : e.getLookup().lookupAll(Planet.class)) {
                PlanetTopComponent.openFor(WindowManager.getDefault(), p);
            }
        }
    };

    /** Flips the explored flag of every selected planet; the nodes in the context fire the change. */
    static final Action TOGGLE_EXPLORED = new AbstractAction(Bundle.CTL_ToggleExplored()) {
        @Override
        public void actionPerformed(ActionEvent e) {
            for (final PlanetNode n : e.getLookup().lookupAll(PlanetNode.class)) {
                change(n.planet, "explored", new Runnable() {
                    @Override
                    public void run() {
                        n.planet.setExplored(!n.planet.isExplored());
                    }
                });
            }
        }
    };

    public Planet getPlanet() {
        return planet;
    }

    @Override
    public String getHtmlDisplayName() {
        return planet.isExplored() ? null : "<i>" + planet.getName() + "</i>";
    }

    @Override
    public void setName(final String n) {
        String old = planet.getName();
        if (old != null && old.equals(n)) {
            super.setName(n);
            return;
        }
        change(planet, "name", new Runnable() {
            @Override
            public void run() {
                planet.setName(n);
            }
        });
        UISession.current().publish(new LogLine(Bundle.MSG_Renamed(old, n)));
    }

    @Override
    public void destroy() throws IOException {
        SolarSystem.get().remove(planet);
        UISession.current().publish(new LogLine(Bundle.MSG_Deleted(planet.getName())));
        super.destroy();
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = new Sheet();
        Sheet.Set set = Sheet.createPropertiesSet();
        set.put(new PropertySupport.Name(this, Bundle.PROP_Name(), Bundle.HINT_PlanetName()));
        set.put(new PropertySupport.ReadWrite<Double>("gravity", Double.class, Bundle.PROP_Gravity(), Bundle.HINT_Gravity()) {
            @Override
            public Double getValue() {
                return planet.getGravity();
            }

            @Override
            public void setValue(Double v) {
                if (v == null || v <= 0) {
                    throw new IllegalArgumentException("Gravity must be positive");
                }
                final Double nv = v;
                change(planet, "gravity", new Runnable() {
                    @Override
                    public void run() {
                        planet.setGravity(nv);
                    }
                });
            }
        });
        set.put(new PropertySupport.ReadWrite<Integer>("moons", Integer.class, Bundle.PROP_Moons(), Bundle.HINT_Moons()) {
            @Override
            public Integer getValue() {
                return planet.getMoons();
            }

            @Override
            public void setValue(Integer v) {
                if (v == null || v < 0) {
                    throw new IllegalArgumentException("Moons cannot be negative");
                }
                final Integer nv = v;
                change(planet, "moons", new Runnable() {
                    @Override
                    public void run() {
                        planet.setMoons(nv);
                    }
                });
            }
        });
        PropertySupport.ReadWrite<Planet.Type> type = new PropertySupport.ReadWrite<Planet.Type>("type", Planet.Type.class, Bundle.PROP_Type(), Bundle.HINT_Type()) {
            @Override
            public Planet.Type getValue() {
                return planet.getType();
            }

            @Override
            public void setValue(Planet.Type v) {
                final Planet.Type nv = v;
                change(planet, "type", new Runnable() {
                    @Override
                    public void run() {
                        planet.setType(nv);
                    }
                });
            }
        };
        // the label and icon come from Planet.Type (Labeled); the renderer adds a muted style for dwarf planets
        type.setRenderer(new java.util.function.Function<Planet.Type, org.webbeans.ui.table.Cell>() {
            @Override
            public org.webbeans.ui.table.Cell apply(Planet.Type t) {
                return t == null ? null : org.webbeans.ui.table.Cell.of(t.getDisplayName(), t.getIcon()).style(t == Planet.Type.DWARF ? "wb-muted" : null);
            }
        });
        set.put(type);
        set.put(new PropertySupport.ReadWrite<Boolean>("explored", Boolean.class, Bundle.PROP_Explored(), Bundle.HINT_Explored()) {
            @Override
            public Boolean getValue() {
                return planet.isExplored();
            }

            @Override
            public void setValue(Boolean v) {
                final boolean nv = Boolean.TRUE.equals(v);
                change(planet, "explored", new Runnable() {
                    @Override
                    public void run() {
                        planet.setExplored(nv);
                    }
                });
            }
        });
        sheet.put(set);
        return sheet;
    }

    @Override
    public Handle getHandle() {
        return new Handle() {
            @Override
            public String getType() {
                return "planet";
            }

            @Override
            public Map<String, Object> toMap() {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("name", planet.getName());
                return m;
            }
        };
    }

    /** Restores a planet node from its handle data ({@code Nodes/Handles/planet}). */
    @Registration(path = "Nodes/Handles", name = "planet", type = NodeHandleFactory.class)
    public static final class HandleFactory implements NodeHandleFactory {
        @Override
        public Node restore(Map<String, Object> data) {
            Planet p = SolarSystem.get().find(String.valueOf(data.get("name")));
            return p == null ? null : new PlanetNode(p);
        }
    }
}
