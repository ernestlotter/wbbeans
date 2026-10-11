package org.webbeans.demo;

import org.webbeans.core.i18n.Messages;
import org.webbeans.ui.components.Html;
import org.webbeans.ui.components.Layout;
import org.webbeans.windows.TopComponent;

/** A read-only page in the editor area. */
@TopComponent.Registration(mode = "editor", openAtStartup = true, position = 200)
@TopComponent.Description(preferredID = "AboutTC", iconBase = "org/webbeans/demo/icons/info.svg")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenAbout", position = 200)
@Messages({
    "CTL_AboutTC=About",
    "CTL_OpenAbout=About",
    "HTML_About=<h2>WebBeans</h2><p>A rich client platform in the spirit of the <b>NetBeans Platform</b>: "
        + "modules, Lookup, a registry, sessions and a delta protocol. This page is a server-side Java component "
        + "tree; the browser runs a generic renderer.</p><p>Try it: drag the tabs around, drop one at the edge of "
        + "another window, double-click a tab to maximize, right-click a tab for more. The layout is saved per user "
        + "and restored when you come back.</p>"
})
public final class AboutTopComponent extends TopComponent {

    public AboutTopComponent() {
        setDisplayName(Bundle.CTL_AboutTC());
        setLayout(new Layout.Fill());
        setPadding(16);
        setScrollable(true);
        add(new Html(Bundle.HTML_About()).setStyleName("demo-about"));
    }
}
