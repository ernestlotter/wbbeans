package org.webbeans.ui.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small Markdown-to-HTML converter for help pages: ATX headings (with ids), paragraphs, fenced code blocks,
 * inline code, bold, italics, links, images, auto-links, ordered and unordered lists (nested by indentation),
 * block quotes, horizontal rules, pipe tables and raw HTML blocks. Unknown constructs stay text; the result is
 * sanitised by the viewer anyway. Not a full CommonMark implementation — enough for documentation.
 */
public final class Markdown {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern LIST_ITEM = Pattern.compile("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$");
    private static final Pattern TABLE_SEP = Pattern.compile("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$");
    private static final Pattern HR = Pattern.compile("^\\s*([-*_])(\\s*\\1){2,}\\s*$");
    private static final Pattern HTML_BLOCK = Pattern.compile("^<(/?[a-zA-Z][a-zA-Z0-9]*)(\\s[^>]*)?>.*");
    private static final Pattern CODE_SPAN = Pattern.compile("`([^`]+)`");
    private static final Pattern INLINE_TAG = Pattern.compile("</?[a-zA-Z][a-zA-Z0-9]*(?:\\s+[a-zA-Z-]+(?:=(?:\"[^\"]*\"|'[^']*'|[^\\s>]+))?)*\\s*/?>");
    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern AUTOLINK = Pattern.compile("&lt;(https?://[^&\\s]+)&gt;");
    private static final Pattern BOLD = Pattern.compile("(\\*\\*|__)(?=\\S)(.+?)(?<=\\S)\\1");
    private static final Pattern ITALIC = Pattern.compile("(?<![\\w*])(\\*|_)(?=\\S)(.+?)(?<=\\S)\\1(?![\\w*])");
    private static final Pattern STRIKE = Pattern.compile("~~(?=\\S)(.+?)(?<=\\S)~~");

    private Markdown() {
    }

    /** Converts a Markdown document to an HTML fragment. */
    public static String toHtml(String markdown) {
        if (markdown == null) {
            return "";
        }
        List<String> lines = new ArrayList<String>();
        for (String l : markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            lines.add(l.replace("\t", "    "));
        }
        StringBuilder out = new StringBuilder(markdown.length() * 2);
        blocks(lines, out);
        return out.toString();
    }

    private static void blocks(List<String> lines, StringBuilder out) {
        int i = 0;
        List<String> para = new ArrayList<String>();
        while (i < lines.size()) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                flushParagraph(para, out);
                i++;
                continue;
            }
            // fenced code
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                flushParagraph(para, out);
                String fence = trimmed.substring(0, 3);
                String lang = trimmed.substring(3).trim();
                StringBuilder code = new StringBuilder();
                i++;
                while (i < lines.size() && !lines.get(i).trim().startsWith(fence)) {
                    code.append(escape(lines.get(i))).append('\n');
                    i++;
                }
                i++;
                out.append("<pre><code").append(lang.isEmpty() ? "" : " class=\"language-" + escape(lang.replaceAll("[^A-Za-z0-9_+-]", "")) + "\"")
                        .append('>').append(code).append("</code></pre>\n");
                continue;
            }
            Matcher h = HEADING.matcher(line);
            if (h.matches()) {
                flushParagraph(para, out);
                int level = h.group(1).length();
                String text = h.group(2);
                out.append("<h").append(level).append(" id=\"").append(slug(text)).append("\">").append(inline(text)).append("</h").append(level).append(">\n");
                i++;
                continue;
            }
            if (HR.matcher(line).matches()) {
                flushParagraph(para, out);
                out.append("<hr>\n");
                i++;
                continue;
            }
            if (trimmed.startsWith(">")) {
                flushParagraph(para, out);
                List<String> inner = new ArrayList<String>();
                while (i < lines.size() && lines.get(i).trim().startsWith(">")) {
                    String t = lines.get(i).trim().substring(1);
                    inner.add(t.startsWith(" ") ? t.substring(1) : t);
                    i++;
                }
                out.append("<blockquote>\n");
                blocks(inner, out);
                out.append("</blockquote>\n");
                continue;
            }
            if (LIST_ITEM.matcher(line).matches()) {
                flushParagraph(para, out);
                List<String> block = new ArrayList<String>();
                while (i < lines.size()) {
                    String l = lines.get(i);
                    if (l.trim().isEmpty()) {
                        // a blank line ends the list unless the next line is indented (continuation) or another item
                        if (i + 1 < lines.size() && (LIST_ITEM.matcher(lines.get(i + 1)).matches() || lines.get(i + 1).startsWith("  "))) {
                            block.add("");
                            i++;
                            continue;
                        }
                        break;
                    }
                    if (!LIST_ITEM.matcher(l).matches() && !l.startsWith(" ") && !block.isEmpty() && !block.get(block.size() - 1).isEmpty()) {
                        block.add(l);  // lazy continuation of the item text
                        i++;
                        continue;
                    }
                    if (!LIST_ITEM.matcher(l).matches() && !l.startsWith(" ")) {
                        break;
                    }
                    block.add(l);
                    i++;
                }
                list(block, out);
                continue;
            }
            if (trimmed.contains("|") && i + 1 < lines.size() && TABLE_SEP.matcher(lines.get(i + 1)).matches()) {
                flushParagraph(para, out);
                List<String> rows = new ArrayList<String>();
                while (i < lines.size() && lines.get(i).trim().contains("|")) {
                    rows.add(lines.get(i));
                    i++;
                }
                table(rows, out);
                continue;
            }
            if (HTML_BLOCK.matcher(trimmed).matches()) {
                flushParagraph(para, out);
                while (i < lines.size() && !lines.get(i).trim().isEmpty()) {
                    out.append(lines.get(i)).append('\n');
                    i++;
                }
                continue;
            }
            para.add(line);
            i++;
        }
        flushParagraph(para, out);
    }

    private static void flushParagraph(List<String> para, StringBuilder out) {
        if (para.isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (int k = 0; k < para.size(); k++) {
            String l = para.get(k);
            boolean hardBreak = l.endsWith("  ");
            text.append(l.trim());
            if (k < para.size() - 1) {
                text.append(hardBreak ? "<br>" : " ");
            }
        }
        out.append("<p>").append(inline(text.toString())).append("</p>\n");
        para.clear();
    }

    /** A list block: items at the smallest indentation, their indented continuation lines as nested blocks. */
    private static void list(List<String> block, StringBuilder out) {
        int base = -1;
        for (String l : block) {
            Matcher m = LIST_ITEM.matcher(l);
            if (m.matches()) {
                int ind = m.group(1).length();
                if (base < 0 || ind < base) {
                    base = ind;
                }
            }
        }
        // runs of items of one kind: "- a / - b" then "1. c" are two lists
        List<List<String>> items = new ArrayList<List<String>>();
        List<Boolean> kinds = new ArrayList<Boolean>();
        List<String> current = null;
        for (String l : block) {
            Matcher m = LIST_ITEM.matcher(l);
            if (m.matches() && m.group(1).length() == base) {
                current = new ArrayList<String>();
                current.add(m.group(3));
                items.add(current);
                kinds.add(Character.isDigit(m.group(2).charAt(0)));
            } else if (current != null) {
                String stripped = l.length() >= base + 2 ? l.substring(Math.min(l.length(), base + 2)) : l.trim();
                current.add(stripped);
            }
        }
        int from = 0;
        while (from < items.size()) {
            int to = from;
            while (to < items.size() && kinds.get(to).equals(kinds.get(from))) {
                to++;
            }
            items(items.subList(from, to), kinds.get(from), out);
            from = to;
        }
    }

    private static void items(List<List<String>> items, boolean ordered, StringBuilder out) {
        out.append(ordered ? "<ol>\n" : "<ul>\n");
        for (List<String> item : items) {
            out.append("<li>").append(inline(item.get(0)));
            List<String> rest = item.subList(1, item.size());
            boolean hasBlocks = false;
            for (String r : rest) {
                if (!r.trim().isEmpty()) {
                    hasBlocks = true;
                }
            }
            if (hasBlocks) {
                boolean nestedList = LIST_ITEM.matcher(firstNonBlank(rest)).matches();
                if (!nestedList) {
                    out.append(' ');
                }
                StringBuilder nested = new StringBuilder();
                blocks(new ArrayList<String>(rest), nested);
                String n = nested.toString();
                if (!nestedList && n.startsWith("<p>") && n.indexOf("<p>", 3) < 0) {
                    n = n.substring(3).replace("</p>\n", "");   // a single continuation paragraph stays inline
                }
                out.append(n);
            }
            out.append("</li>\n");
        }
        out.append(ordered ? "</ol>\n" : "</ul>\n");
    }

    private static String firstNonBlank(List<String> lines) {
        for (String l : lines) {
            if (!l.trim().isEmpty()) {
                return l;
            }
        }
        return "";
    }

    private static void table(List<String> rows, StringBuilder out) {
        out.append("<table>\n<thead>\n<tr>");
        for (String cell : cells(rows.get(0))) {
            out.append("<th>").append(inline(cell)).append("</th>");
        }
        out.append("</tr>\n</thead>\n<tbody>\n");
        for (int r = 2; r < rows.size(); r++) {
            out.append("<tr>");
            for (String cell : cells(rows.get(r))) {
                out.append("<td>").append(inline(cell)).append("</td>");
            }
            out.append("</tr>\n");
        }
        out.append("</tbody>\n</table>\n");
    }

    private static List<String> cells(String row) {
        String r = row.trim();
        if (r.startsWith("|")) {
            r = r.substring(1);
        }
        if (r.endsWith("|")) {
            r = r.substring(0, r.length() - 1);
        }
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean code = false;
        for (int i = 0; i < r.length(); i++) {
            char c = r.charAt(i);
            if (c == '`') {
                code = !code;
            }
            if (c == '|' && !code && (i == 0 || r.charAt(i - 1) != '\\')) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else if (c == '|') {
                cur.setLength(cur.length() - 1);
                cur.append('|');
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out;
    }

    /** Inline markup of one line or paragraph (the text is escaped here; code spans and inline HTML tags such as {@code <kbd>} pass through). */
    public static String inline(String text) {
        List<String> codes = new ArrayList<String>();
        Matcher m = CODE_SPAN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            codes.add("<code>" + escape(m.group(1)) + "</code>");
            m.appendReplacement(sb, Matcher.quoteReplacement("\u0000" + (codes.size() - 1) + "\u0000"));
        }
        m.appendTail(sb);
        m = INLINE_TAG.matcher(sb.toString());
        sb = new StringBuffer();
        while (m.find()) {
            codes.add(m.group());          // kept verbatim; the viewer's sanitiser decides what survives
            m.appendReplacement(sb, Matcher.quoteReplacement("\u0000" + (codes.size() - 1) + "\u0000"));
        }
        m.appendTail(sb);
        String s = escape(sb.toString());
        s = replace(IMAGE, s, "<img src=\"$2\" alt=\"$1\">");
        s = replace(LINK, s, "<a href=\"$2\">$1</a>");
        s = replace(AUTOLINK, s, "<a href=\"$1\">$1</a>");
        s = replace(BOLD, s, "<strong>$2</strong>");
        s = replace(ITALIC, s, "<em>$2</em>");
        s = replace(STRIKE, s, "<s>$1</s>");
        for (int i = 0; i < codes.size(); i++) {
            s = s.replace("\u0000" + i + "\u0000", codes.get(i));
        }
        return s;
    }

    private static String replace(Pattern p, String s, String replacement) {
        return p.matcher(s).replaceAll(replacement);
    }

    public static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    /** {@code "Saving a file"} → {@code saving-a-file}; the id of a heading. */
    public static String slug(String text) {
        String s = text.toLowerCase(Locale.ROOT).replaceAll("[`*_\\[\\]()]", "").replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "section" : Character.isLetter(s.charAt(0)) ? s : "h-" + s;
    }
}
