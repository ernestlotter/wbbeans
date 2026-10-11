package org.webbeans.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.webbeans.core.json.JsonValue;

/** Turns property values and call arguments into JSON-ready values: components become {@code {"$ref": id}}. */
public final class JsonCodec {

    private JsonCodec() {
    }

    public static Object encode(Object v) {
        if (v == null || v instanceof String || v instanceof Number || v instanceof Boolean) {
            return v;
        }
        if (v instanceof Component) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("$ref", ((Component) v).getId());
            return m;
        }
        if (v instanceof JsonValue) {
            return encode(((JsonValue) v).toJson());
        }
        if (v instanceof Enum) {
            return ((Enum<?>) v).name();
        }
        if (v instanceof Map) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                m.put(String.valueOf(e.getKey()), encode(e.getValue()));
            }
            return m;
        }
        if (v instanceof Collection) {
            List<Object> l = new ArrayList<Object>();
            for (Object o : (Collection<?>) v) {
                l.add(encode(o));
            }
            return l;
        }
        if (v.getClass().isArray()) {
            List<Object> l = new ArrayList<Object>();
            int n = java.lang.reflect.Array.getLength(v);
            for (int i = 0; i < n; i++) {
                l.add(encode(java.lang.reflect.Array.get(v, i)));
            }
            return l;
        }
        if (v instanceof java.time.temporal.TemporalAccessor) {
            return v.toString();
        }
        return v.toString();
    }

    public static Map<String, Object> encodeProps(Map<String, Object> props) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> e : props.entrySet()) {
            m.put(e.getKey(), encode(e.getValue()));
        }
        return m;
    }
}
