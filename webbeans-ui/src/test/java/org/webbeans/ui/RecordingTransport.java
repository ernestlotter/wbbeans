package org.webbeans.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.webbeans.core.json.Json;

/** Test transport: keeps every message sent. */
public class RecordingTransport implements Transport {

    public final List<String> messages = new ArrayList<String>();
    public boolean closed;

    @Override
    public void send(String json) {
        messages.add(json);
    }

    @Override
    public void close() {
        closed = true;
    }

    public Map<String, Object> last() {
        return Json.parseObject(messages.get(messages.size() - 1));
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> lastOps() {
        return (List<Map<String, Object>>) (List<?>) Json.array(last(), "ops");
    }

    public void clear() {
        messages.clear();
    }
}
