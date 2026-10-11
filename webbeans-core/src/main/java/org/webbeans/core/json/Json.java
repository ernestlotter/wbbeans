package org.webbeans.core.json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader/writer, enough for the WebBeans wire protocol. Values map to {@code Map<String,Object>}
 * (insertion ordered), {@code List<Object>}, {@code String}, {@code Long}/{@code Double}, {@code Boolean} and
 * {@code null}. Writing additionally accepts arrays, any {@link Number}, {@link Collection}s, enums (by name),
 * {@link CharSequence}s and objects implementing {@link JsonValue}.
 */
public final class Json {

    private Json() {
    }

    // ---- writing ---------------------------------------------------------------------------------------------

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder(256);
        write(value, sb);
        return sb.toString();
    }

    public static void write(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof JsonValue) {
            write(((JsonValue) value).toJson(), sb);
        } else if (value instanceof CharSequence) {
            quote(value.toString(), sb);
        } else if (value instanceof Boolean) {
            sb.append(((Boolean) value).booleanValue());
        } else if (value instanceof Number) {
            writeNumber((Number) value, sb);
        } else if (value instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                quote(String.valueOf(e.getKey()), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (value instanceof Collection) {
            sb.append('[');
            boolean first = true;
            for (Object o : (Collection<?>) value) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(o, sb);
            }
            sb.append(']');
        } else if (value.getClass().isArray()) {
            sb.append('[');
            int n = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                write(java.lang.reflect.Array.get(value, i), sb);
            }
            sb.append(']');
        } else if (value instanceof Enum) {
            quote(((Enum<?>) value).name(), sb);
        } else if (value instanceof Character) {
            quote(value.toString(), sb);
        } else {
            quote(value.toString(), sb);
        }
    }

    private static void writeNumber(Number n, StringBuilder sb) {
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
        } else {
            sb.append(n.toString());
        }
    }

    public static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '<':
                    sb.append("\\u003c"); // keeps JSON safe inside <script> blocks
                    break;
                case ' ':
                    sb.append("\\u2028");
                    break;
                case ' ':
                    sb.append("\\u2029");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ---- reading ---------------------------------------------------------------------------------------------

    /** Parses a JSON document. @throws IllegalArgumentException on malformed input */
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (p.pos != text.length()) {
            throw p.error("Trailing characters");
        }
        return v;
    }

    /** Parses a document that must be an object. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        return (Map<String, Object>) v;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("JSON: " + msg + " at " + pos);
        }

        void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object value() {
            if (pos >= s.length()) {
                throw error("Unexpected end");
            }
            char c = s.charAt(pos);
            switch (c) {
                case '{':
                    return object();
                case '[':
                    return array();
                case '"':
                    return string();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return number();
                    }
                    throw error("Unexpected character '" + c + "'");
            }
        }

        private void expect(String word) {
            if (!s.startsWith(word, pos)) {
                throw error("Expected " + word);
            }
            pos += word.length();
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            pos++;
            skipWs();
            if (peek() == '}') {
                pos++;
                return m;
            }
            while (true) {
                skipWs();
                if (peek() != '"') {
                    throw error("Expected string key");
                }
                String k = string();
                skipWs();
                if (peek() != ':') {
                    throw error("Expected ':'");
                }
                pos++;
                skipWs();
                m.put(k, value());
                skipWs();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    return m;
                } else {
                    throw error("Expected ',' or '}'");
                }
            }
        }

        private List<Object> array() {
            List<Object> l = new ArrayList<Object>();
            pos++;
            skipWs();
            if (peek() == ']') {
                pos++;
                return l;
            }
            while (true) {
                skipWs();
                l.add(value());
                skipWs();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    return l;
                } else {
                    throw error("Expected ',' or ']'");
                }
            }
        }

        private char peek() {
            if (pos >= s.length()) {
                throw error("Unexpected end");
            }
            return s.charAt(pos);
        }

        private String string() {
            pos++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= s.length()) {
                    throw error("Unterminated string");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= s.length()) {
                        throw error("Bad escape");
                    }
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > s.length()) {
                                throw error("Bad unicode escape");
                            }
                            try {
                                sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            } catch (NumberFormatException ex) {
                                throw error("Bad unicode escape");
                            }
                            pos += 4;
                            break;
                        default:
                            throw error("Bad escape '\\" + e + "'");
                    }
                } else if (c < 0x20) {
                    throw error("Control character in string");
                } else {
                    sb.append(c);
                }
            }
        }

        private Number number() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            boolean floating = false;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    floating = true;
                    pos++;
                } else {
                    break;
                }
            }
            String t = s.substring(start, pos);
            try {
                if (!floating) {
                    long l = Long.parseLong(t);
                    return l;
                }
                return Double.parseDouble(t);
            } catch (NumberFormatException e) {
                throw error("Bad number '" + t + "'");
            }
        }
    }

    // ---- convenience accessors for parsed maps ----------------------------------------------------------------

    public static String string(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    public static long integer(Map<String, Object> m, String key, long def) {
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).longValue() : def;
    }

    public static double number(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : def;
    }

    public static boolean bool(Map<String, Object> m, String key) {
        return Boolean.TRUE.equals(m.get(key));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof List ? (List<Object>) v : null;
    }
}
