package org.webbeans.core.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class JsonTest {

    @Test
    public void roundTrip() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("t", "delta");
        m.put("seq", 412L);
        m.put("ok", true);
        m.put("none", null);
        m.put("pi", 3.25);
        m.put("ops", Arrays.asList(1L, "two", Arrays.asList(3L)));
        m.put("s", "quote\" back\\ nl\n tab\t <script> \u2028 é");
        String json = Json.write(m);
        assertTrue(json, json.contains("\\u003cscript>"));
        Map<String, Object> back = Json.parseObject(json);
        assertEquals(m, back);
    }

    @Test
    public void numbersAndTypes() {
        assertEquals("1", Json.write(1.0));
        assertEquals("1.5", Json.write(1.5));
        assertEquals("null", Json.write(Double.NaN));
        assertEquals("\"RED\"", Json.write(Thread.State.RUNNABLE).replace("RUNNABLE", "RED"));
        assertEquals("[1,2]", Json.write(new int[] {1, 2}));
        assertEquals("{\"a\":1}", Json.write(new JsonValue() {
            public Object toJson() { Map<String, Object> m = new LinkedHashMap<String, Object>(); m.put("a", 1); return m; }
        }));
        assertEquals(Long.valueOf(-12), Json.parse("-12"));
        assertEquals(Double.valueOf(1e3), Json.parse("1e3"));
        assertNull(Json.parse(" null "));
        assertEquals("a\u00e9\u20ac", Json.parse("\"a\\u00e9\\u20ac\""));
    }

    @Test
    public void malformed() {
        for (String bad : new String[] {"{", "[1,]", "{\"a\" 1}", "tru", "\"abc", "1 2", "{\"a\":1,}", "\"\\x\""}) {
            try {
                Json.parse(bad);
                fail(bad);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().startsWith("JSON:"));
            }
        }
    }

    @Test
    public void accessors() {
        Map<String, Object> m = Json.parseObject("{\"id\":18,\"type\":\"click\",\"data\":{\"x\":1.5},\"list\":[1]}");
        assertEquals(18, Json.integer(m, "id", 0));
        assertEquals("click", Json.string(m, "type"));
        assertEquals(1.5, Json.number(Json.object(m, "data"), "x", 0), 0);
        List<Object> l = Json.array(m, "list");
        assertEquals(1, l.size());
        assertNull(Json.object(m, "nope"));
    }
}
