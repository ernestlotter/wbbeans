package org.webbeans.graph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A spring-embedder (Fruchterman–Reingold): edges pull, nodes push, cooling over a fixed number of iterations.
 * Deterministic for a given seed; starts from the current locations so repeated runs refine rather than
 * reshuffle.
 */
public class ForceGraphLayout extends GraphLayout {

    private int iterations = 300;
    private double springLength = 120;
    private long seed = 1;

    public ForceGraphLayout setIterations(int n) {
        iterations = Math.max(1, n);
        return this;
    }

    /** The preferred edge length. */
    public ForceGraphLayout setSpringLength(double px) {
        springLength = Math.max(10, px);
        return this;
    }

    public ForceGraphLayout setSeed(long s) {
        seed = s;
        return this;
    }

    @Override
    public void layout(GraphScene<?, ?> scene, boolean animated) {
        List<NodeWidget> ns = nodes(scene);
        int n = ns.size();
        if (n == 0) {
            return;
        }
        double[] x = new double[n];
        double[] y = new double[n];
        Map<NodeWidget, Integer> index = new HashMap<NodeWidget, Integer>();
        Random rnd = new Random(seed);
        for (int i = 0; i < n; i++) {
            index.put(ns.get(i), i);
            Point p = ns.get(i).getLocation();
            boolean unplaced = p.x == 0 && p.y == 0;
            x[i] = unplaced ? rnd.nextDouble() * springLength * Math.sqrt(n) : p.x;
            y[i] = unplaced ? rnd.nextDouble() * springLength * Math.sqrt(n) : p.y;
        }
        List<EdgeWidget> es = edges(scene);
        double k = springLength;
        double t = k * 2;
        double cool = t / iterations;
        double[] dx = new double[n];
        double[] dy = new double[n];
        for (int it = 0; it < iterations; it++) {
            java.util.Arrays.fill(dx, 0);
            java.util.Arrays.fill(dy, 0);
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double ddx = x[i] - x[j];
                    double ddy = y[i] - y[j];
                    double d = Math.max(0.01, Math.sqrt(ddx * ddx + ddy * ddy));
                    double f = k * k / d;                 // repulsion
                    dx[i] += ddx / d * f;
                    dy[i] += ddy / d * f;
                    dx[j] -= ddx / d * f;
                    dy[j] -= ddy / d * f;
                }
            }
            for (EdgeWidget e : es) {
                Integer a = index.get(e.getSourceNode());
                Integer b = index.get(e.getTargetNode());
                if (a == null || b == null || a.equals(b)) {
                    continue;
                }
                double ddx = x[a] - x[b];
                double ddy = y[a] - y[b];
                double d = Math.max(0.01, Math.sqrt(ddx * ddx + ddy * ddy));
                double f = d * d / k;                     // attraction
                dx[a] -= ddx / d * f;
                dy[a] -= ddy / d * f;
                dx[b] += ddx / d * f;
                dy[b] += ddy / d * f;
            }
            for (int i = 0; i < n; i++) {
                double d = Math.max(0.01, Math.sqrt(dx[i] * dx[i] + dy[i] * dy[i]));
                double step = Math.min(d, t);
                x[i] += dx[i] / d * step;
                y[i] += dy[i] / d * step;
            }
            t = Math.max(1, t - cool);
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, x[i] - ns.get(i).getSize().width / 2);
            minY = Math.min(minY, y[i] - ns.get(i).getSize().height / 2);
        }
        for (int i = 0; i < n; i++) {
            ns.get(i).setLocation(new Point(Math.round(x[i] - minX), Math.round(y[i] - minY)), animated);
        }
    }
}
