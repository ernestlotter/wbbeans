package org.webbeans.demo;

import java.util.function.Consumer;

import org.webbeans.core.i18n.Messages;

import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.TextArea;
import org.webbeans.windows.TopComponent;

/** Shows {@link LogLine}s from the session bus: whatever any window of this user publishes, in any tab. */
@TopComponent.Registration(mode = "output", openAtStartup = true, position = 100)
@TopComponent.Description(preferredID = "EventsTC", iconBase = "org/webbeans/demo/icons/log.svg", persistenceType = TopComponent.PERSISTENCE_ONLY_OPENED)
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenEvents", position = 300)
@Messages({"CTL_EventsTC=Events", "CTL_OpenEvents=Events", "LBL_Events=Events received by the server appear here."})
public final class EventsTopComponent extends TopComponent {

    private final TextArea log = new TextArea();
    private final StringBuilder lines = new StringBuilder();
    private org.webbeans.ui.Registration subscription; // qualified: TopComponent.Registration shadows it

    public EventsTopComponent() {
        setDisplayName(Bundle.CTL_EventsTC());
        setLayout(new Layout.Border());
        log.setMonospace(true).setReadOnly(true).setTestId("log");
        add(new Label(Bundle.LBL_Events()), Layout.Border.NORTH);
        add(log, Layout.Border.CENTER);
    }

    @Override
    protected void componentOpened() {
        subscription = getUI().getSession().subscribe(LogLine.class, new Consumer<LogLine>() {
            @Override
            public void accept(LogLine l) {
                lines.append(l.text).append('\n');
                log.setValue(lines.toString());
                log.scrollToLine(Integer.MAX_VALUE);
            }
        });
    }

    @Override
    protected void componentClosed() {
        if (subscription != null) {
            subscription.remove();
            subscription = null;
        }
    }
}
