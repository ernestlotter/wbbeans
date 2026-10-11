package org.webbeans.workbench.dashboard;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.openide.util.lookup.ServiceProvider;

/**
 * A global (JVM-wide) source of synthetic metrics every dashboard reads: CPU as a random walk with occasional
 * spikes, requests per path, traffic by source. Real applications replace this service with one over JMX,
 * Micrometer or whatever they monitor.
 */
@ServiceProvider(service = MetricsSampler.class)
public class MetricsSampler {

    private final Random random = new Random();
    private double cpu = 35;
    private int tick;

    /** CPU load in percent for this second. */
    public synchronized double cpu() {
        tick++;
        cpu += random.nextGaussian() * 4;
        if (tick % 45 == 0) {
            cpu += 45;   // a spike every 45 s exercises the high-load notification
        }
        cpu = Math.max(2, Math.min(99, cpu * 0.97 + 35 * 0.03));
        return Math.round(cpu * 10) / 10.0;
    }

    /** Requests per second by path. */
    public synchronized Map<String, Double> requestsByPath() {
        Map<String, Double> m = new LinkedHashMap<String, Double>();
        m.put("/api", 120 + random.nextInt(60) + cpu);
        m.put("/static", 40.0 + random.nextInt(30));
        m.put("/ws", 10.0 + random.nextInt(15));
        m.put("/auth", 2.0 + random.nextInt(6));
        return m;
    }

    /** Share of traffic by source (sums to roughly 100). */
    public synchronized Map<String, Double> trafficBySource() {
        Map<String, Double> m = new LinkedHashMap<String, Double>();
        double browsers = 55 + random.nextInt(10), mobile = 25 + random.nextInt(8);
        m.put("Browsers", browsers);
        m.put("Mobile", mobile);
        m.put("API clients", Math.max(1, 100 - browsers - mobile - 3));
        m.put("Bots", 3.0);
        return m;
    }
}
