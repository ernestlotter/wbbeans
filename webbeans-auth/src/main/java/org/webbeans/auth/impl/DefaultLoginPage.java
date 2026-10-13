package org.webbeans.auth.impl;

import java.util.Locale;

import org.openide.util.lookup.ServiceProvider;
import org.webbeans.auth.Challenge;
import org.webbeans.auth.LoginField;
import org.webbeans.auth.LoginMethod;
import org.webbeans.auth.LoginPageModel;
import org.webbeans.auth.spi.AuthenticationProvider;
import org.webbeans.auth.spi.LoginPage;
import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.json.Json;

/**
 * The standard login page: every provider's method in order — forms post to {@code /login}, redirect methods
 * are buttons to {@code /auth/<id>/start}, script methods load their SDK and script. Self-contained HTML, no
 * dependency on the application bundle (nothing is rendered before authentication).
 */
@ServiceProvider(service = LoginPage.class, position = 10000)
public final class DefaultLoginPage implements LoginPage {

    @Override
    public String render(LoginPageModel m) {
        Locale locale = m.getLocale();
        StringBuilder sb = new StringBuilder(4096);
        String title = esc(m.getApplicationTitle());
        sb.append("<!doctype html><html lang=\"").append(esc(locale.getLanguage())).append("\"><head><meta charset=\"utf-8\">")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>").append(title).append("</title>")
          .append("<style>").append(CSS).append("</style></head><body><main class=\"login\"><div class=\"card\">")
          .append("<h1>").append(title).append("</h1><p class=\"sub\">").append(esc(msg(locale, "#MSG_SignIn"))).append("</p>");
        if (m.getErrorMessage() != null) {
            sb.append("<div class=\"err\" role=\"alert\">").append(esc(m.getErrorMessage())).append("</div>");
        }
        String cp = esc(m.getContextPath());
        Challenge c = m.getChallenge();
        if (c != null) {
            sb.append("<form method=\"post\" action=\"").append(cp).append("/auth/challenge\">")
              .append(hidden("csrf", m.getCsrfToken())).append(hidden("challenge", c.getId())).append(hidden("return", m.getReturnUrl()))
              .append("<h2>").append(esc(msg(locale, c.getTitle()))).append("</h2>");
            for (LoginField f : c.getFields()) {
                field(sb, f, locale, null);
            }
            sb.append("<button class=\"btn primary\" type=\"submit\">").append(esc(msg(locale, "#BTN_Continue"))).append("</button></form>");
        } else {
            boolean first = true;
            for (AuthenticationProvider p : m.getProviders()) {
                LoginMethod lm = p.getLoginMethod();
                if (lm == null) {
                    continue;
                }
                if (!first) {
                    sb.append("<div class=\"or\">").append(esc(msg(locale, "#LBL_Or"))).append("</div>");
                }
                first = false;
                switch (lm.getKind()) {
                    case FORM:
                        sb.append("<form method=\"post\" action=\"").append(cp).append("/login\" autocomplete=\"on\">")
                          .append(hidden("csrf", m.getCsrfToken())).append(hidden("provider", p.getId())).append(hidden("return", m.getReturnUrl()));
                        for (LoginField f : lm.getFields()) {
                            field(sb, f, locale, p);
                        }
                        sb.append("<button class=\"btn primary\" type=\"submit\">").append(esc(resolve(p, lm.getButtonLabel(), locale)))
                          .append("</button></form>");
                        break;
                    case REDIRECT:
                        sb.append("<a class=\"btn redirect\" href=\"").append(cp).append("/auth/").append(esc(p.getId())).append("/start?return=")
                          .append(url(m.getReturnUrl())).append("\">");
                        if (lm.getIcon() != null) {
                            sb.append("<img alt=\"\" src=\"").append(cp).append("/wb/res/").append(esc(lm.getIcon())).append("\">");
                        }
                        sb.append(esc(resolve(p, lm.getButtonLabel(), locale))).append("</a>");
                        break;
                    case SCRIPT:
                        sb.append("<div class=\"script\" id=\"auth-").append(esc(p.getId())).append("\"></div>");
                        for (String ext : lm.getExternalScripts()) {
                            sb.append("<script src=\"").append(esc(ext)).append("\"></script>");
                        }
                        sb.append("<script>window.WB_AUTH_CONFIG=window.WB_AUTH_CONFIG||{};window.WB_AUTH_CONFIG[")
                          .append(Json.write(p.getId())).append("]={config:").append(Json.write(lm.getConfig()))
                          .append(",tokenUrl:").append(Json.write(m.getContextPath() + "/auth/token")).append(",csrf:").append(Json.write(m.getCsrfToken()))
                          .append(",returnUrl:").append(Json.write(m.getReturnUrl())).append(",container:")
                          .append(Json.write("auth-" + p.getId())).append("};</script>")
                          .append("<script src=\"").append(cp).append("/wb/res/").append(esc(lm.getScriptResource())).append("\"></script>");
                        break;
                    default:
                        break;
                }
            }
            if (first) {
                sb.append("<p class=\"sub\">").append(esc(msg(locale, "#MSG_NoProviders"))).append("</p>");
            }
        }
        sb.append("</div></main></body></html>");
        return sb.toString();
    }

    private static void field(StringBuilder sb, LoginField f, Locale locale, AuthenticationProvider p) {
        String id = "f-" + esc(f.getName());
        String label = p != null ? resolve(p, f.getLabel(), locale) : msg(locale, f.getLabel());
        switch (f.getKind()) {
            case HIDDEN:
                sb.append(hidden(f.getName(), f.getDefaultValue()));
                return;
            case CHECKBOX:
                sb.append("<label class=\"check\"><input type=\"checkbox\" name=\"").append(esc(f.getName())).append("\" value=\"true\"> ")
                  .append(esc(label)).append("</label>");
                return;
            case CHOICE:
                sb.append("<label for=\"").append(id).append("\">").append(esc(label)).append("</label><select id=\"").append(id)
                  .append("\" name=\"").append(esc(f.getName())).append("\">");
                for (String c : f.getChoices()) {
                    sb.append("<option value=\"").append(esc(c)).append("\">").append(esc(c)).append("</option>");
                }
                sb.append("</select>");
                return;
            case PASSWORD:
            case TEXT:
            default:
                sb.append("<label for=\"").append(id).append("\">").append(esc(label)).append("</label><input id=\"").append(id)
                  .append("\" name=\"").append(esc(f.getName())).append("\" type=\"").append(f.getKind() == LoginField.Kind.PASSWORD ? "password" : "text")
                  .append("\"").append(f.isRequired() ? " required" : "")
                  .append(f.getKind() == LoginField.Kind.PASSWORD ? " autocomplete=\"current-password\"" : f.getName().equals("username") ? " autocomplete=\"username\" autofocus" : "")
                  .append(f.getDefaultValue() != null ? " value=\"" + esc(f.getDefaultValue()) + "\"" : "").append(">");
        }
    }

    private static String hidden(String name, String value) {
        return "<input type=\"hidden\" name=\"" + esc(name) + "\" value=\"" + esc(value == null ? "" : value) + "\">";
    }

    /** Resolves a {@code #KEY} against the provider's own bundle, falling back to the platform bundle. */
    public static String resolve(AuthenticationProvider p, String text, Locale locale) {
        if (text == null) {
            return "";
        }
        if (!text.startsWith("#")) {
            return text;
        }
        String r = Bundles.find(p.getClass().getClassLoader(), Bundles.bundleNameOf(p.getClass()), text.substring(1), locale);
        return r != null ? r : msg(locale, text);
    }

    public static String msg(Locale locale, String text) {
        if (text == null) {
            return "";
        }
        if (!text.startsWith("#")) {
            return text;
        }
        String r = Bundles.resolve(DefaultLoginPage.class.getClassLoader(), "org.webbeans.auth.Bundle", text, locale);
        return r.equals(text) ? text.substring(1) : r;
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '&': sb.append("&amp;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    static String url(String s) {
        try {
            return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String CSS =
        ":root{--bg:#eef1f4;--panel:#fff;--line:#c6ced7;--fg:#1c2530;--fg2:#5b6876;--accent:#0f7f86;--danger:#b43b3b;color-scheme:light dark}"
        + "@media(prefers-color-scheme:dark){:root{--bg:#161b21;--panel:#1d232b;--line:#38424e;--fg:#e4e9ee;--fg2:#97a4b2;--accent:#3fb6bd;--danger:#e06767}}"
        + "html,body{height:100%;margin:0}body{background:var(--bg);color:var(--fg);font:14px/1.45 system-ui,-apple-system,'Segoe UI',sans-serif}"
        + ".login{min-height:100%;display:grid;place-items:center;padding:16px;box-sizing:border-box}"
        + ".card{width:min(100%,360px);background:var(--panel);border:1px solid var(--line);border-radius:8px;padding:28px 28px 22px;box-shadow:0 8px 28px rgba(0,0,0,.15)}"
        + "h1{margin:0 0 4px;font-size:20px}h2{margin:12px 0 4px;font-size:15px}.sub{margin:0 0 18px;color:var(--fg2)}"
        + "label{display:block;font-size:12px;color:var(--fg2);margin:10px 0 4px}label.check{display:flex;gap:8px;align-items:center;color:var(--fg);font-size:13px}"
        + "input:not([type=checkbox]),select{width:100%;box-sizing:border-box;padding:8px 10px;border:1px solid var(--line);border-radius:4px;background:var(--bg);color:var(--fg);font:inherit}"
        + ".btn{display:block;width:100%;box-sizing:border-box;margin-top:16px;padding:9px 12px;border:1px solid var(--line);border-radius:4px;background:var(--panel);color:var(--fg);font:inherit;text-align:center;text-decoration:none;cursor:pointer}"
        + ".btn.primary{background:var(--accent);color:#fff;border-color:var(--accent)}.btn.redirect img{height:18px;vertical-align:-4px;margin-right:8px}"
        + ".err{color:var(--danger);margin:0 0 8px}.or{text-align:center;color:var(--fg2);margin:14px 0 2px;font-size:12px;text-transform:uppercase;letter-spacing:.06em}";
}
