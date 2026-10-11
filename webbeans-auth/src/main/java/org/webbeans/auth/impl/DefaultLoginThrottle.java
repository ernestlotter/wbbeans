package org.webbeans.auth.impl;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.webbeans.auth.spi.LoginThrottle;

/** Sliding window: a key is blocked once it has {@code maxFailures} failures within {@code window}. */
public final class DefaultLoginThrottle implements LoginThrottle {

    private final int maxFailures;
    private final long windowMs;
    private final Map<String, Deque<Long>> failures = new LinkedHashMap<String, Deque<Long>>();

    public DefaultLoginThrottle(int maxFailures, Duration window) {
        this.maxFailures = maxFailures;
        this.windowMs = window.toMillis();
    }

    @Override
    public synchronized boolean isBlocked(String key) {
        Deque<Long> d = failures.get(key);
        if (d == null) {
            return false;
        }
        prune(d);
        return d.size() >= maxFailures;
    }

    @Override
    public synchronized void recordFailure(String key) {
        Deque<Long> d = failures.get(key);
        if (d == null) {
            d = new ArrayDeque<Long>();
            failures.put(key, d);
        }
        d.addLast(System.currentTimeMillis());
        prune(d);
        if (failures.size() > 10000) { // bound memory under attack
            Iterator<String> it = failures.keySet().iterator();
            it.next();
            it.remove();
        }
    }

    @Override
    public synchronized void recordSuccess(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Long> d) {
        long cutoff = System.currentTimeMillis() - windowMs;
        while (!d.isEmpty() && d.peekFirst() < cutoff) {
            d.pollFirst();
        }
    }
}
