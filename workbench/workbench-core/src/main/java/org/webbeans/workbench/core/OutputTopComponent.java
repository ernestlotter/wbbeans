package org.webbeans.workbench.core;

import java.text.DateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.function.Consumer;

import org.webbeans.actions.ActionMap;
import org.webbeans.core.i18n.Messages;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.TextArea;
import org.webbeans.windows.TopComponent;
import org.webbeans.workbench.core.api.LogMessage;

/**
 * The Output window: every {@link LogMessage} published on the session bus, with a time stamp, in every tab.
 * Keeps the last 500 lines; Edit ▸ Delete (or the Delete key) clears it through the window's {@link ActionMap}.
 */
@TopComponent.Registration(mode = "output", openAtStartup = true, position = 100)
@TopComponent.Description(preferredID = "OutputTC", iconBase = "org/webbeans/workbench/core/icons/output.svg", persistenceType = TopComponent.PERSISTENCE_ONLY_OPENED, helpId = "workbench.output")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenOutput", position = 900)
@Messages({"CTL_OutputTC=Output", "CTL_OpenOutput=Output"})
public final class OutputTopComponent extends TopComponent {

    private static final int MAX_LINES = 500;

    private final TextArea text = new TextArea();
    private final Deque<String> lines = new ArrayDeque<String>();
    private org.webbeans.ui.Registration subscription;

    public OutputTopComponent() {
        setDisplayName(Bundle.CTL_OutputTC());
        setLayout(new Layout.Border());
        text.setMonospace(true).setReadOnly(true).setTestId("output");
        add(text, Layout.Border.CENTER);
        ActionMap map = new ActionMap();
        map.put(ActionMap.DELETE, new org.webbeans.actions.AbstractAction("clear") {
            @Override
            public void actionPerformed(org.webbeans.actions.ActionEvent e) {
                lines.clear();
                text.setValue("");
            }
        });
        associateLookup(org.openide.util.lookup.Lookups.singleton(map));
    }

    @Override
    protected void localeChanged() {
        setDisplayName(Bundle.CTL_OutputTC());
    }

    @Override
    protected void componentOpened() {
        subscription = getUI().getSession().subscribe(LogMessage.class, new Consumer<LogMessage>() {
            @Override
            public void accept(LogMessage m) {
                append(m);
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

    private void append(LogMessage m) {
        DateFormat f = DateFormat.getTimeInstance(DateFormat.MEDIUM, getUI().getSession().getLocale());
        String prefix = m.getLevel() == LogMessage.Level.INFO ? "" : m.getLevel() + " ";
        lines.addLast(f.format(new Date(m.getTime().toEpochMilli())) + " " + prefix + "[" + m.getSource() + "] " + m.getText());
        while (lines.size() > MAX_LINES) {
            lines.removeFirst();
        }
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            sb.append(l).append('\n');
        }
        text.setValue(sb.toString());
        text.scrollToLine(Integer.MAX_VALUE);
    }
}
