package org.webbeans.core.registry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * A user's customisation of the registry: entries hidden and positions overridden (menu/toolbar customisation).
 * It can only hide or reorder, never add. Persisted as a simple text file ({@code path=hidden} or
 * {@code path=<position>}, one per line) — {@code users/<user>/config/registry.properties}.
 */
public final class UserOverlay {

    private static final String HIDDEN = "hidden";

    private final Map<String, String> edits = new LinkedHashMap<String, String>();
    private final List<ChangeListener> listeners = new CopyOnWriteArrayList<ChangeListener>();

    public synchronized boolean isHidden(String path) {
        return HIDDEN.equals(edits.get(path));
    }

    /** The overridden position, or null. */
    public synchronized Integer getPosition(String path) {
        String v = edits.get(path);
        if (v == null || HIDDEN.equals(v)) {
            return null;
        }
        return Integer.valueOf(v);
    }

    public void hide(String path) {
        synchronized (this) {
            edits.put(path, HIDDEN);
        }
        fire();
    }

    public void position(String path, int position) {
        synchronized (this) {
            edits.put(path, Integer.toString(position));
        }
        fire();
    }

    /** Removes any customisation of the path. */
    public void reset(String path) {
        synchronized (this) {
            edits.remove(path);
        }
        fire();
    }

    public void clear() {
        synchronized (this) {
            edits.clear();
        }
        fire();
    }

    public synchronized Map<String, String> asMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(edits));
    }

    public void addChangeListener(ChangeListener l) {
        listeners.add(l);
    }

    public void removeChangeListener(ChangeListener l) {
        listeners.remove(l);
    }

    private void fire() {
        ChangeEvent ev = new ChangeEvent(this);
        for (ChangeListener l : listeners) {
            l.stateChanged(ev);
        }
    }

    public static UserOverlay load(Path file) throws IOException {
        UserOverlay o = new UserOverlay();
        if (!Files.exists(file)) {
            return o;
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.lastIndexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String path = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (HIDDEN.equals(value)) {
                    o.edits.put(path, HIDDEN);
                } else {
                    try {
                        o.edits.put(path, Integer.toString(Integer.parseInt(value)));
                    } catch (NumberFormatException ignored) {
                        // skip malformed line
                    }
                }
            }
        } finally {
            r.close();
        }
        return o;
    }

    public void save(Path file) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.createDirectories(file.getParent());
        Writer w = new OutputStreamWriter(Files.newOutputStream(tmp), StandardCharsets.UTF_8);
        try {
            w.write("# WebBeans registry customisations: <path>=hidden | <path>=<position>\n");
            for (Map.Entry<String, String> e : asMap().entrySet()) {
                w.write(e.getKey() + "=" + e.getValue() + "\n");
            }
        } finally {
            w.close();
        }
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
}
