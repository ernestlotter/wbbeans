package org.webbeans.help;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openide.util.Lookup;
import org.webbeans.ui.text.Markdown;
import org.webbeans.ui.components.Html;

/**
 * Loads and renders help pages: the resource of a {@link HelpSet.Topic} (a localised variant
 * {@code name_<lang>.ext} first), Markdown converted to HTML, relative links resolved — to {@code help:<id>}
 * when they point at another registered page, to {@code wb/res/<path>} (images, downloads; relative to the
 * application) otherwise — and
 * the result sanitised with the {@link Html} allow-list.
 */
public final class HelpContent {

    private static final Pattern URL_ATTR = Pattern.compile("(href|src)=\"([^\"]*)\"");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    private HelpContent() {
    }

    /** The sanitised HTML of a topic, or a "page missing" note. */
    public static String html(HelpSet set, HelpSet.Topic topic, Locale locale) {
        String raw = load(topic.getResource(), locale);
        if (raw == null) {
            return "<p class=\"wb-help-missing\">" + Html.sanitize(Markdown.escape(topic.getResource())) + "</p>";
        }
        String html = topic.getResource().toLowerCase(Locale.ROOT).endsWith(".md") ? Markdown.toHtml(raw) : raw;
        return Html.sanitize(resolveLinks(set, topic, html));
    }

    /** The plain text of a topic (for search). */
    public static String text(HelpSet set, HelpSet.Topic topic, Locale locale) {
        return TAG.matcher(html(set, topic, locale)).replaceAll(" ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"");
    }

    /** Reads a class-path resource of the module path, trying {@code base_<lang>.ext} before {@code base.ext}. */
    public static String load(String resource, Locale locale) {
        if (resource == null) {
            return null;
        }
        ClassLoader l = Lookup.getDefault().lookup(ClassLoader.class);
        if (l == null) {
            l = HelpContent.class.getClassLoader();
        }
        if (locale != null && !locale.getLanguage().isEmpty()) {
            int dot = resource.lastIndexOf('.');
            String localized = dot > resource.lastIndexOf('/') ? resource.substring(0, dot) + "_" + locale.getLanguage() + resource.substring(dot) : resource + "_" + locale.getLanguage();
            String s = read(l, localized);
            if (s != null) {
                return s;
            }
        }
        return read(l, resource);
    }

    private static String read(ClassLoader l, String resource) {
        InputStream in = l.getResourceAsStream(resource);
        if (in == null) {
            return null;
        }
        try {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                }
                return new String(bos.toByteArray(), StandardCharsets.UTF_8);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    /** Relative {@code href}/{@code src} values → {@code help:<id>} for registered pages, else module resources. */
    static String resolveLinks(HelpSet set, HelpSet.Topic topic, String html) {
        String dir = topic.getResource().contains("/") ? topic.getResource().substring(0, topic.getResource().lastIndexOf('/') + 1) : "";
        Matcher m = URL_ATTR.matcher(html);
        StringBuffer sb = new StringBuffer(html.length());
        while (m.find()) {
            String attr = m.group(1), value = m.group(2);
            String replaced = value;
            if (!value.isEmpty() && !value.startsWith("#") && !value.startsWith("/") && !value.matches("[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
                String fragment = "";
                int hash = value.indexOf('#');
                if (hash >= 0) {
                    fragment = value.substring(hash);
                    value = value.substring(0, hash);
                }
                String path = normalize(dir + value);
                HelpSet.Topic target = set == null ? null : set.byResource(path);
                replaced = target != null ? "help:" + target.getId() + fragment : "wb/res/" + path + fragment;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(attr + "=\"" + replaced + "\""));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String normalize(String path) {
        java.util.Deque<String> parts = new java.util.ArrayDeque<String>();
        for (String p : path.split("/")) {
            if (p.equals("..")) {
                if (!parts.isEmpty()) {
                    parts.removeLast();
                }
            } else if (!p.isEmpty() && !p.equals(".")) {
                parts.addLast(p);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(p);
        }
        return sb.toString();
    }
}
