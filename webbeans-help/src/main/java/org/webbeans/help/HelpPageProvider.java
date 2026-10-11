package org.webbeans.help;

import java.util.List;
import java.util.Map;

import org.openide.util.lookup.ServiceProvider;
import org.webbeans.ui.text.Markdown;
import org.webbeans.ui.PageProvider;
import org.webbeans.ui.UISession;

/**
 * {@code GET /wb/help/<id>}: a help page as a stand-alone HTML document (table of contents on the left, the page
 * on the right) for a browser tab of its own, bookmarks and printing. Signed-in users only; the same role
 * filtering and localisation as the Help window, because the front end renders on the session executor.
 */
@ServiceProvider(service = PageProvider.class)
public final class HelpPageProvider implements PageProvider {

    public static final String PREFIX = "/wb/help/";

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public String render(UISession session, String basePath, String path, Map<String, String> query) {
        HelpSet set = HelpSet.current();
        String id = path == null ? "" : path.trim();
        while (id.endsWith("/")) {
            id = id.substring(0, id.length() - 1);
        }
        HelpSet.Topic topic = id.isEmpty() ? set.getHome() : set.find(id);
        if (topic == null) {
            return null;
        }
        String base = basePath == null ? "" : basePath;
        String body = HelpContent.html(set, topic, session.getLocale()).replace("href=\"help:", "href=\"" + base + PREFIX)
                .replace("src=\"wb/res/", "src=\"" + base + "/wb/res/").replace("href=\"wb/res/", "href=\"" + base + "/wb/res/");
        StringBuilder html = new StringBuilder(4096);
        html.append("<!doctype html><html lang=\"").append(Markdown.escape(session.getLocale().getLanguage())).append("\"><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
            .append("<title>").append(Markdown.escape(topic.getTitle())).append("</title>")
            .append("<style>").append(CSS).append("</style></head><body><nav class=\"toc\">");
        toc(set.getRoots(), topic, base, html);
        html.append("</nav><main><div class=\"crumbs\">");
        List<HelpSet.Topic> pathTopics = topic.getPath();
        for (int i = 0; i < pathTopics.size(); i++) {
            if (i > 0) {
                html.append(" › ");
            }
            html.append(Markdown.escape(pathTopics.get(i).getTitle()));
        }
        html.append("</div><article class=\"wb-help\">").append(body).append("</article></main></body></html>");
        return html.toString();
    }

    private static void toc(List<HelpSet.Topic> topics, HelpSet.Topic current, String base, StringBuilder html) {
        if (topics.isEmpty()) {
            return;
        }
        html.append("<ul>");
        for (HelpSet.Topic t : topics) {
            html.append("<li").append(t == current ? " class=\"current\"" : "").append("><a href=\"").append(base).append(PREFIX)
                .append(Markdown.escape(t.getId())).append("\">").append(Markdown.escape(t.getTitle())).append("</a>");
            toc(t.getChildren(), current, base, html);
            html.append("</li>");
        }
        html.append("</ul>");
    }

    private static final String CSS = ":root{--fg:#1c2530;--fg2:#5b6876;--bg:#ffffff;--chrome:#eef1f4;--line:#c6ced7;--accent:#0f7f86}"
            + "@media(prefers-color-scheme:dark){:root{--fg:#e4e9ee;--fg2:#97a4b2;--bg:#1d232b;--chrome:#232a33;--line:#38424e;--accent:#3fb6bd}}"
            + "body{margin:0;display:flex;min-height:100vh;font:14px/1.5 system-ui,-apple-system,'Segoe UI',sans-serif;color:var(--fg);background:var(--bg)}"
            + "nav.toc{width:260px;flex:none;background:var(--chrome);border-right:1px solid var(--line);padding:16px 12px;box-sizing:border-box}"
            + "nav.toc ul{list-style:none;margin:0;padding-left:12px}nav.toc>ul{padding-left:0}nav.toc li{margin:3px 0}nav.toc a{color:var(--fg);text-decoration:none}"
            + "nav.toc li.current>a{color:var(--accent);font-weight:600}main{flex:1;padding:16px 32px 48px;max-width:860px}.crumbs{color:var(--fg2);font-size:12px}"
            + "article h1{font-size:24px;margin:8px 0 12px}article h2{font-size:18px;margin:22px 0 6px;padding-bottom:3px;border-bottom:1px solid var(--line)}"
            + "article code{font-family:ui-monospace,Menlo,Consolas,monospace;font-size:12.5px;background:var(--chrome);padding:1px 4px;border-radius:3px}"
            + "article pre{background:var(--chrome);border:1px solid var(--line);border-radius:6px;padding:8px 10px;overflow:auto}article pre code{background:none;padding:0}"
            + "article kbd{font:inherit;font-size:12px;border:1px solid var(--line);border-bottom-width:2px;border-radius:4px;padding:0 5px;background:var(--chrome)}"
            + "article table{border-collapse:collapse}article th,article td{border:1px solid var(--line);padding:4px 8px;text-align:left}article th{background:var(--chrome)}"
            + "article blockquote{margin:8px 0;padding:4px 12px;border-left:3px solid var(--accent);color:var(--fg2)}article img{max-width:100%}article a{color:var(--accent)}"
            + "@media(max-width:700px){body{display:block}nav.toc{width:auto;border-right:0;border-bottom:1px solid var(--line)}}";
}
