package org.webbeans.help;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.EventType;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Property;
import org.webbeans.ui.Registration;

/**
 * Renders a help page ({@code wb.HelpViewer}): sanitised HTML with help typography, scrollable. Clicks on
 * {@code help:<id>} and {@code #anchor} links are reported as {@link #NAVIGATE} events instead of being followed;
 * other links open in a new browser tab. {@link #scrollTo} scrolls to a heading id.
 */
@Renderer(id = "wb.HelpViewer", resource = "org/webbeans/help/client/help.js", css = "org/webbeans/help/client/help.css")
public final class HelpViewer extends Component {

    /** Already sanitised HTML (see {@link HelpContent#html}). */
    public static final Property<String> HTML = Property.of("html", String.class, "");

    /** A link inside the page was clicked; {@code href} is {@code help:<id>[#anchor]} or {@code #anchor}. */
    public static final EventType<NavigateEvent> NAVIGATE = EventType.of("navigate", NavigateEvent::new);

    public static final class NavigateEvent extends Event {
        public NavigateEvent(Component source, java.util.Map<String, Object> data) {
            super(source, data);
        }

        public String getHref() {
            Object h = getData().get("href");
            return h == null ? "" : String.valueOf(h);
        }
    }

    public HelpViewer() {
        super("wb.HelpViewer");
    }

    public HelpViewer setHtml(String sanitizedHtml) {
        set(HTML, sanitizedHtml == null ? "" : sanitizedHtml);
        return this;
    }

    public Registration addNavigateListener(Listener<NavigateEvent> l) {
        return addListener(NAVIGATE, l);
    }

    /** Scrolls to the element with the id (a heading slug), or to the top for null. */
    public void scrollTo(String anchor) {
        call("scrollTo", anchor == null ? "" : anchor);
    }
}
