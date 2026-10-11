package org.webbeans.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The long-poll {@link org.webbeans.ui.Transport}: the session thread queues messages, an HTTP thread waits for
 * them in {@link #poll}. One instance per {@code hello}; a reconnect creates a new one and the UI closes the old.
 * The fallback for clients without a WebSocket ({@link WebSocketTransport}).
 */
public final class PollTransport implements ServerTransport {

    private final List<String> queue = new ArrayList<String>();
    private boolean closed;
    private volatile long lastPoll = System.currentTimeMillis();

    @Override
    public synchronized void send(String json) {
        if (!closed) {
            queue.add(json);
            notifyAll();
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        notifyAll();
    }

    @Override
    public synchronized boolean isClosed() {
        return closed;
    }

    /** Milliseconds since the client last asked this transport for messages. */
    @Override
    public long idleMillis() {
        return System.currentTimeMillis() - lastPoll;
    }

    /** Takes whatever is queued without waiting. */
    public synchronized List<String> drain() {
        lastPoll = System.currentTimeMillis();
        List<String> out = new ArrayList<String>(queue);
        queue.clear();
        return out;
    }

    /** Waits up to the timeout for at least one message; returns an empty list on timeout or close. */
    public synchronized List<String> poll(long timeout, TimeUnit unit) throws InterruptedException {
        lastPoll = System.currentTimeMillis();
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (queue.isEmpty() && !closed) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                break;
            }
            TimeUnit.NANOSECONDS.timedWait(this, left);
        }
        lastPoll = System.currentTimeMillis();
        List<String> out = new ArrayList<String>(queue);
        queue.clear();
        return out;
    }
}
