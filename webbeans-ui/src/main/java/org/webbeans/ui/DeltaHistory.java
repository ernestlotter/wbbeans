package org.webbeans.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** The last messages sent to a UI, for replay after a reconnect. Bounded by count and bytes. */
public final class DeltaHistory {

    static final class Entry {
        final long seq;
        final String json;

        Entry(long seq, String json) {
            this.seq = seq;
            this.json = json;
        }
    }

    private final int maxMessages;
    private final int maxBytes;
    private final Deque<Entry> entries = new ArrayDeque<Entry>();
    private int bytes;

    public DeltaHistory(int maxMessages, int maxBytes) {
        this.maxMessages = maxMessages;
        this.maxBytes = maxBytes;
    }

    synchronized void add(long seq, String json) {
        entries.addLast(new Entry(seq, json));
        bytes += json.length();
        while (entries.size() > maxMessages || bytes > maxBytes) {
            bytes -= entries.pollFirst().json.length();
        }
    }

    /** Messages after {@code lastSeq}, or null if they are no longer all available (a full resync is needed). */
    synchronized List<String> since(long lastSeq, long currentSeq) {
        if (lastSeq == currentSeq) {
            return new ArrayList<String>();
        }
        if (entries.isEmpty() || entries.peekFirst().seq > lastSeq + 1) {
            return null;
        }
        List<String> out = new ArrayList<String>();
        for (Entry e : entries) {
            if (e.seq > lastSeq) {
                out.add(e.json);
            }
        }
        return out;
    }

    synchronized void clear() {
        entries.clear();
        bytes = 0;
    }
}
