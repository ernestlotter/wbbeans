package org.webbeans.ui.components;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Property;

/** Rich read-only content from a sanitised HTML subset. */
@Renderer(id = "wb.Html", resource = "org/webbeans/ui/client/renderers/html.js")
public class Html extends Component {

    public static final Property<String> HTML = Property.of("html", String.class, "");

    private static final Set<String> TAGS = new HashSet<String>(Arrays.asList(
            "b", "i", "u", "s", "em", "strong", "code", "pre", "br", "p", "ul", "ol", "li", "a", "span", "h1", "h2", "h3", "h4",
            "table", "thead", "tbody", "tr", "th", "td", "blockquote", "hr", "small", "sub", "sup",
            "h5", "h6", "kbd", "div", "dl", "dt", "dd", "caption", "img"));
    private static final Pattern ID = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]*");
    private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z0-9]+)([^>]*)>");
    private static final Pattern ATTR = Pattern.compile("([a-zA-Z-]+)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");

    public Html() {
        super("wb.Html");
    }

    public Html(String html) {
        this();
        setHtml(html);
    }

    public Html setHtml(String html) {
        set(HTML, sanitize(html));
        return this;
    }

    /**
     * Allow-list sanitiser: unknown tags are escaped; only {@code href} (http/https/mailto/help/relative) on links,
     * {@code src} (same origin, https or inline data) on images, {@code alt}, {@code title}, {@code id} and
     * {@code class} survive; {@code a} gets {@code rel="noopener"} on the client.
     */
    public static String sanitize(String html) {
        if (html == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(html.length());
        Matcher m = TAG.matcher(html);
        int last = 0;
        while (m.find()) {
            out.append(escapeText(html.substring(last, m.start())));
            String tag = m.group(2).toLowerCase();
            if (TAGS.contains(tag)) {
                out.append('<').append(m.group(1)).append(tag);
                if (m.group(1).isEmpty()) {
                    Matcher a = ATTR.matcher(m.group(3));
                    while (a.find()) {
                        String name = a.group(1).toLowerCase();
                        String value = a.group(2);
                        if (value.startsWith("\"") || value.startsWith("'")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        if (name.equals("href") && tag.equals("a") && safeUrl(value)) {
                            out.append(" href=\"").append(escapeAttr(value)).append('"');
                        } else if (name.equals("src") && tag.equals("img") && safeImage(value)) {
                            out.append(" src=\"").append(escapeAttr(value)).append('"');
                        } else if ((name.equals("alt") || name.equals("title")) && !value.contains("<")) {
                            out.append(' ').append(name).append("=\"").append(escapeAttr(value)).append('"');
                        } else if (name.equals("id") && ID.matcher(value).matches()) {
                            out.append(" id=\"").append(value).append('"');
                        } else if (name.equals("class")) {
                            out.append(" class=\"").append(escapeAttr(value)).append('"');
                        }
                    }
                }
                out.append('>');
            } else {
                out.append(escapeText(m.group(0)));
            }
            last = m.end();
        }
        out.append(escapeText(html.substring(last)));
        return out.toString();
    }

    private static boolean safeUrl(String v) {
        String s = v.trim().toLowerCase();
        return s.startsWith("http://") || s.startsWith("https://") || s.startsWith("mailto:") || s.startsWith("help:") || s.startsWith("/")
                || s.startsWith("#") || (!s.contains(":") && !s.startsWith("//"));
    }

    /** Images only from the application itself (module resources, blobs) or https. */
    private static boolean safeImage(String v) {
        String s = v.trim().toLowerCase();
        return s.startsWith("https://") || (s.startsWith("/") && !s.startsWith("//")) || s.startsWith("data:image/")
                || (!s.contains(":") && !s.startsWith("//"));
    }

    static String escapeText(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '&' && !s.startsWith("&amp;", i) && !s.startsWith("&lt;", i) && !s.startsWith("&gt;", i) && !s.startsWith("&quot;", i) && !s.startsWith("&#", i)) {
                sb.append("&amp;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static String escapeAttr(String s) {
        return escapeText(s).replace("\"", "&quot;");
    }
}
