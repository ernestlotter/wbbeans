package org.webbeans.workbench.viewer3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.webbeans.core.registry.Registration;
import org.webbeans.scene3d.Vector3;
import org.webbeans.ui.UISession;

/**
 * The session's assembly: a turbine of hub, shaft, housing, blades and sensors, with a selection shared by the
 * Parts tree and the 3D viewer, and a simulation that heats sensors on a background thread and hops onto the
 * session executor ({@code session.access}) to publish the changes — server push without any UI code knowing.
 */
@Registration(path = "Services/Session", type = PartsModel.class)
public final class PartsModel implements AutoCloseable {

    /** Something changed: the part list, a part's state, or the selection. */
    public interface Listener {
        void partsChanged();

        void partChanged(Part p);

        void selectionChanged(Part selected);
    }

    private static final ScheduledExecutorService SIM = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "workbench-simulation");
            t.setDaemon(true);
            return t;
        }
    });

    private volatile UISession session;   // bound on first use from a session task (services are created before any task runs)
    private final List<Part> parts = new ArrayList<Part>();
    private final List<Listener> listeners = new ArrayList<Listener>();
    private final Random random = new Random();
    private Part selected;
    private ScheduledFuture<?> job;
    private boolean spinning = true;
    private int blades;

    public PartsModel() {
        parts.add(new Part("hub", "Hub", Part.Kind.HUB, 0x9aa3b2, new Vector3(0, 0, 0), 0));
        parts.add(new Part("shaft", "Shaft", Part.Kind.SHAFT, 0x6b7785, new Vector3(0, 0, -2.5), 0));
        parts.add(new Part("housing", "Housing", Part.Kind.HOUSING, 0x3b4a5a, new Vector3(0, 0, -3), 0));
        setBladeCount(6);
        parts.add(new Part("sensor-a", "Sensor A", Part.Kind.SENSOR, 0x3fb6bd, new Vector3(2.4, 2.4, -3), 0));
        parts.add(new Part("sensor-b", "Sensor B", Part.Kind.SENSOR, 0x3fb6bd, new Vector3(-2.4, 2.4, -3), 0));
        parts.add(new Part("sensor-c", "Sensor C", Part.Kind.SENSOR, 0x3fb6bd, new Vector3(0, -3.2, -3), 0));
    }

    public static PartsModel get() {
        return UISession.current().getLookup().lookup(PartsModel.class);
    }

    public synchronized List<Part> getParts() {
        return Collections.unmodifiableList(new ArrayList<Part>(parts));
    }

    public synchronized Part find(String id) {
        for (Part p : parts) {
            if (p.getId().equals(id)) {
                return p;
            }
        }
        return null;
    }

    public synchronized int getBladeCount() {
        return blades;
    }

    /** Rebuilds the blades (the views diff by id: unchanged blades keep their nodes). */
    public void setBladeCount(int n) {
        synchronized (this) {
            n = Math.max(2, Math.min(12, n));
            for (java.util.Iterator<Part> it = parts.iterator(); it.hasNext();) {
                if (it.next().getKind() == Part.Kind.BLADE) {
                    it.remove();
                }
            }
            int at = 1;
            for (int i = 0; i < n; i++) {
                parts.add(at++, new Part("blade-" + (i + 1), "Blade " + (i + 1), Part.Kind.BLADE, 0xd9822b, new Vector3(0, 0, 0), i * 2 * Math.PI / n));
            }
            blades = n;
        }
        fire(null, true, false);
    }

    public synchronized Part getSelected() {
        return selected;
    }

    public void setSelected(Part p) {
        synchronized (this) {
            if (selected == p) {
                return;
            }
            selected = p;
        }
        for (Listener l : snapshot()) {
            l.selectionChanged(p);
        }
    }

    public synchronized boolean isSpinning() {
        return spinning;
    }

    public void setSpinning(boolean b) {
        synchronized (this) {
            spinning = b;
        }
        fire(null, true, false);
    }

    public void rename(Part p, String name) {
        p.setName(name);
        fire(p, false, false);
    }

    public void recolor(Part p, int rgb) {
        p.setColor(rgb);
        fire(p, false, false);
    }

    /** Registers a view; the simulation starts with the first one (on the session executor, which binds the session). */
    public synchronized void addListener(Listener l) {
        listeners.add(l);
        if (session == null) {
            session = UISession.current();
        }
        if (job == null && session != null) {
            start();
        }
    }

    public synchronized void removeListener(Listener l) {
        listeners.remove(l);
    }

    private synchronized List<Listener> snapshot() {
        return new ArrayList<Listener>(listeners);
    }

    private void fire(Part p, boolean structure, boolean unused) {
        for (Listener l : snapshot()) {
            if (structure) {
                l.partsChanged();
            } else {
                l.partChanged(p);
            }
        }
    }

    // ---- simulation (background thread → session executor) -------------------------------------------------------

    private void start() {
        job = SIM.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                step();
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);
    }

    /** Runs on the simulation thread: computes, then publishes on the session executor. */
    private void step() {
        final List<Part> changed = new ArrayList<Part>();
        synchronized (this) {
            for (Part p : parts) {
                if (p.getKind() == Part.Kind.SENSOR) {
                    double t = p.getTemperature() + (spinning ? 1.5 : -2) + random.nextGaussian();
                    t = Math.max(20, Math.min(110, t));
                    p.setTemperature(Math.round(t * 10) / 10.0);
                    boolean ok = t < 85;
                    if (ok != p.isOk()) {
                        p.setOk(ok);
                    }
                    changed.add(p);
                }
            }
        }
        UISession s = session;
        if (s == null) {
            return;
        }
        if (s.getState() == UISession.State.CLOSED) {
            close();
            return;
        }
        s.access(new Runnable() {
            @Override
            public void run() {
                for (Part p : changed) {
                    fire(p, false, false);
                }
            }
        });
    }

    @Override
    public void close() {
        if (job != null) {
            job.cancel(false);
            job = null;
        }
    }
}
