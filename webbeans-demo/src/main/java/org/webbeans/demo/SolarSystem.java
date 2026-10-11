package org.webbeans.demo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.webbeans.core.registry.Registration;
import org.webbeans.ui.UISession;

/**
 * The per-user model behind the Planets window: one instance per session ({@code Services/Session}), so every
 * tab of the user sees the same data. Listeners are told about structural changes; nodes mirror them.
 */
@Registration(path = "Services/Session", type = SolarSystem.class)
public final class SolarSystem {

    public interface Listener {
        /** Planets were added or removed. */
        void changed();

        /** A property of a planet changed (every node of that planet refreshes). */
        void planetChanged(Planet p, String property);
    }

    private final List<Planet> planets = new ArrayList<Planet>();
    private final List<Listener> listeners = new ArrayList<Listener>();

    public SolarSystem() {
        planets.add(new Planet("Mercury", 3.7, 0, Planet.Type.ROCKY, true));
        planets.add(new Planet("Venus", 8.87, 0, Planet.Type.ROCKY, true));
        planets.add(new Planet("Earth", 9.81, 1, Planet.Type.ROCKY, true));
        planets.add(new Planet("Mars", 3.71, 2, Planet.Type.ROCKY, true));
        planets.add(new Planet("Jupiter", 24.79, 95, Planet.Type.GAS_GIANT, false));
        planets.add(new Planet("Saturn", 10.44, 146, Planet.Type.GAS_GIANT, false));
        planets.add(new Planet("Uranus", 8.69, 28, Planet.Type.ICE_GIANT, false));
        planets.add(new Planet("Neptune", 11.15, 16, Planet.Type.ICE_GIANT, false));
        planets.add(new Planet("Pluto", 0.62, 5, Planet.Type.DWARF, false));
    }

    /** The current session's instance. */
    public static SolarSystem get() {
        return UISession.current().getLookup().lookup(SolarSystem.class);
    }

    public synchronized List<Planet> getPlanets() {
        return Collections.unmodifiableList(new ArrayList<Planet>(planets));
    }

    public synchronized Planet find(String name) {
        for (Planet p : planets) {
            if (p.getName().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    public void add(Planet p) {
        synchronized (this) {
            planets.add(p);
        }
        fire();
    }

    public void remove(Planet p) {
        synchronized (this) {
            planets.remove(p);
        }
        fire();
    }

    public synchronized void addListener(Listener l) {
        listeners.add(l);
    }

    public synchronized void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void fire() {
        for (Listener l : snapshot()) {
            l.changed();
        }
    }

    /** Tells every listener (every node of the planet, in every window) that a property changed. */
    public void planetChanged(Planet p, String property) {
        for (Listener l : snapshot()) {
            l.planetChanged(p, property);
        }
    }

    private List<Listener> snapshot() {
        synchronized (this) {
            return new ArrayList<Listener>(listeners);
        }
    }
}
