package org.webbeans.demo;

import java.util.ArrayList;
import java.util.List;

import org.webbeans.core.i18n.Messages;
import org.webbeans.nodes.AbstractNode;
import org.webbeans.nodes.ChildFactory;
import org.webbeans.nodes.Children;
import org.webbeans.nodes.Node;
import org.webbeans.nodes.PasteType;
import org.webbeans.nodes.Transferable;
import org.webbeans.nodes.Transferables;
import org.webbeans.ui.UISession;

/** The root of the Planets window: an asynchronous child factory over the session's {@link SolarSystem}. */
@Messages({"CTL_SolarSystem=Solar system", "NAME_Copy={0} (copy)"})
public final class SolarSystemNode extends AbstractNode {

    public SolarSystemNode(SolarSystem system) {
        super(Children.create(new PlanetChildFactory(system), true));
        setName("solar-system");
        setDisplayName(Bundle.CTL_SolarSystem());
        setIconBase("org/webbeans/demo/icons/sun.svg");
    }

    /** Pasting planet nodes (Edit ▸ Paste, drag & drop) duplicates the planets; a cut moves nothing (same system). */
    @Override
    public PasteType[] getPasteTypes(final Transferable t) {
        final List<Node> nodes = Transferables.nodesOf(t);
        final List<Planet> planets = new ArrayList<Planet>();
        for (Node n : nodes) {
            Planet p = n.getLookup().lookup(Planet.class);
            if (p != null) {
                planets.add(p);
            }
        }
        if (planets.isEmpty()) {
            return new PasteType[0];
        }
        return new PasteType[] {new PasteType() {
            @Override
            public Transferable paste() {
                for (Planet p : planets) {
                    Planet copy = new Planet(Bundle.NAME_Copy(p.getName()), p.getGravity(), p.getMoons(), p.getType(), p.isExplored());
                    SolarSystem.get().add(copy);
                }
                return null;
            }
        }};
    }

    /** Keys are the planets themselves (identity), so renamed planets keep their nodes. */
    static final class PlanetChildFactory extends ChildFactory<Planet> implements SolarSystem.Listener {
        private final SolarSystem system;

        PlanetChildFactory(SolarSystem system) {
            this.system = system;
            system.addListener(this);
        }

        @Override
        protected boolean createKeys(List<Planet> toPopulate) {
            try {
                Thread.sleep(300); // pretend the data is remote: the wait node shows meanwhile
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            toPopulate.addAll(new ArrayList<Planet>(system.getPlanets()));
            return true;
        }

        @Override
        protected Node createNodeForKey(Planet key) {
            return new PlanetNode(key);
        }

        @Override
        public void changed() {
            // called on the session thread by the demo model; refresh synchronously so the tree updates at once
            if (UISession.current() != null) {
                refresh(true);
            }
        }

        @Override
        public void planetChanged(Planet p, String property) {
        }
    }
}
