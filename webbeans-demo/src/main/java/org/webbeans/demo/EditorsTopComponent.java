package org.webbeans.demo;

import java.util.Arrays;

import org.webbeans.core.i18n.Messages;
import org.webbeans.ui.ChangeEvent;
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.Event;
import org.webbeans.ui.Listener;
import org.webbeans.ui.components.Button;
import org.webbeans.ui.components.CheckBox;
import org.webbeans.ui.components.CodeEditor;
import org.webbeans.ui.components.DiffView;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.MarkdownEditor;
import org.webbeans.ui.components.OutputView;
import org.webbeans.ui.components.Panel;
import org.webbeans.ui.components.TabbedPane;
import org.webbeans.windows.StatusDisplayer;
import org.webbeans.windows.TopComponent;

/** Step 12: the text components — {@link CodeEditor}, {@link MarkdownEditor}, {@link DiffView} and {@link OutputView}. */
@TopComponent.Registration(mode = "editor", openAtStartup = false, position = 170)
@TopComponent.Description(preferredID = "EditorsTC", iconBase = "org/webbeans/demo/icons/form.svg")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenEditors", position = 170)
@Messages({
    "CTL_EditorsTC=Editors",
    "CTL_OpenEditors=Editors",
    "TAB_Code=Code",
    "TAB_Markdown=Markdown",
    "TAB_Diff=Diff",
    "TAB_Output=Output",
    "BTN_Check=Check",
    "BTN_Run=Run",
    "BTN_Clear=Clear",
    "LBL_Context=Collapse unchanged lines",
    "MSG_Cursor=Line {0}, column {1}",
    "MSG_Problems={0} problem(s) found",
    "MSG_Diff={0} added, {1} removed",
    "MSG_LineClicked=Line {0} clicked"
})
public final class EditorsTopComponent extends TopComponent {

    private static final String SAMPLE = "package org.example;\n\nimport java.util.List;\n\n/** A planet. */\npublic class Planet {\n    private final String name;\n    private int moons\n\n    public Planet(String name) {\n        this.name = name;\n    }\n\n    public String getName() {\n        return name;\n    }\n}\n";
    private static final String OLD = "Mercury 0 moons\nVenus 0 moons\nEarth 1 moon\nMars 2 moons\nJupiter 79 moons\nSaturn 82 moons\nUranus 27 moons\nNeptune 14 moons\nPluto 5 moons\n";
    private static final String NEW = "Mercury 0 moons\nVenus 0 moons\nEarth 1 moon\nMars 2 moons\nJupiter 95 moons\nSaturn 146 moons\nUranus 28 moons\nNeptune 16 moons\n";

    private final CodeEditor code = new CodeEditor(CodeEditor.Language.JAVA);
    private final Label cursor = new Label();
    private final MarkdownEditor markdown = new MarkdownEditor("# Planets\n\nThe **inner** planets are rocky:\n\n- Mercury\n- Venus\n- Earth\n- Mars\n\n> Jupiter is the largest.\n");
    private final DiffView diff = new DiffView(OLD, NEW);
    private final OutputView output = new OutputView();
    private int runs;

    public EditorsTopComponent() {
        setDisplayName(Bundle.CTL_EditorsTC());
        setLayout(new Layout.Fill());
        final TabbedPane tabs = new TabbedPane();
        tabs.setTestId("editorTabs");

        // --- code editor with annotations ---
        Panel codePanel = new Panel(new Layout.Border());
        code.setValue(SAMPLE);
        code.setTestId("code");
        code.addCursorListener(new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                cursor.setText(Bundle.MSG_Cursor(e.getInt("line", 1), e.getInt("col", 1)));
            }
        });
        code.addGutterClickListener(new Listener<Event>() {
            @Override
            public void onEvent(Event e) {
                StatusDisplayer.getDefault().setStatusText(Bundle.MSG_LineClicked(e.getInt("line", 0)));
            }
        });
        Button check = new Button(Bundle.BTN_Check());
        check.setTestId("check");
        check.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                code.clearAnnotations();
                int problems = 0;
                String[] lines = code.getValue().split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    String l = lines[i].trim();
                    if (l.startsWith("private") && !l.endsWith(";") && !l.endsWith("{")) {
                        code.addAnnotation(new CodeEditor.Annotation(i + 1, CodeEditor.Annotation.Kind.ERROR, "';' expected"));
                        problems++;
                    }
                    String simple = l.startsWith("import") ? l.substring(l.lastIndexOf('.') + 1, l.length() - 1) : null;
                    if (simple != null && code.getValue().indexOf(simple) == code.getValue().lastIndexOf(simple)) {
                        code.addAnnotation(new CodeEditor.Annotation(i + 1, CodeEditor.Annotation.Kind.WARNING, "Unused import"));
                        problems++;
                    }
                }
                cursor.setText(Bundle.MSG_Problems(problems));
                if (problems > 0) {
                    code.goToLine(code.getAnnotations().get(0).getLine());
                }
            }
        });
        cursor.setTestId("cursor");
        Panel bar = new Panel(new Layout.Flow(Layout.Orientation.HORIZONTAL, 8));
        bar.setPadding(6);
        bar.add(check);
        bar.add(cursor);
        codePanel.add(bar, Layout.Border.NORTH);
        codePanel.add(code, Layout.Border.CENTER);
        tabs.addTab(Bundle.TAB_Code(), codePanel);

        // --- markdown ---
        markdown.setTestId("markdown");
        tabs.addTab(Bundle.TAB_Markdown(), markdown);

        // --- diff ---
        Panel diffPanel = new Panel(new Layout.Border());
        diff.setTitles("moons-2010.txt", "moons-2024.txt").setContext(null).setTestId("diff");
        final CheckBox context = new CheckBox(Bundle.LBL_Context());
        context.setTestId("context");
        context.addChangeListener(new Listener<ChangeEvent<Boolean>>() {
            @Override
            public void onEvent(ChangeEvent<Boolean> e) {
                diff.setContext(context.isSelected() ? Integer.valueOf(1) : null);
            }
        });
        Label stats = new Label(Bundle.MSG_Diff(diff.getAddedCount(), diff.getRemovedCount()));
        stats.setTestId("diffStats");
        Panel diffBar = new Panel(new Layout.Flow(Layout.Orientation.HORIZONTAL, 12));
        diffBar.setPadding(6);
        diffBar.add(context);
        diffBar.add(stats);
        diffPanel.add(diffBar, Layout.Border.NORTH);
        diffPanel.add(diff, Layout.Border.CENTER);
        tabs.addTab(Bundle.TAB_Diff(), diffPanel);

        // --- output ---
        Panel outPanel = new Panel(new Layout.Border());
        output.setTimestamps(true).setTestId("output");
        output.addLinkListener(new Listener<OutputView.LinkEvent>() {
            @Override
            public void onEvent(OutputView.LinkEvent e) {
                if (!e.isUrl()) {
                    tabs.setSelectedIndex(0);
                    code.goToLine(Integer.parseInt(e.getLink()));
                } else {
                    StatusDisplayer.getDefault().setStatusText(e.getLink());
                }
            }
        });
        Button run = new Button(Bundle.BTN_Run());
        run.setKind(Button.Kind.PRIMARY).setTestId("run");
        run.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                runs++;
                output.heading("Build #" + runs);
                output.append("Compiling 3 source files to target/classes");
                output.append("\u001b[32m[INFO]\u001b[0m Planet.java \u001b[1mok\u001b[0m");
                output.append("Planet.java:8: ';' expected", OutputView.Style.ERROR, "8");
                output.warning("Unused import java.util.List");
                output.append("See https://example.org/docs/build for details");
                output.appendAll(Arrays.asList("Tests run: 12, Failures: 0", "BUILD " + (runs % 2 == 0 ? "SUCCESS" : "FAILURE")));
                if (runs % 2 == 0) {
                    output.success("Done in 1.2 s");
                } else {
                    output.error("Done with errors");
                }
            }
        });
        Button clear = new Button(Bundle.BTN_Clear());
        clear.setTestId("clearOutput");
        clear.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                output.clear();
            }
        });
        Panel outBar = new Panel(new Layout.Flow(Layout.Orientation.HORIZONTAL, 8));
        outBar.setPadding(6);
        outBar.add(run);
        outBar.add(clear);
        outPanel.add(outBar, Layout.Border.NORTH);
        outPanel.add(output, Layout.Border.CENTER);
        tabs.addTab(Bundle.TAB_Output(), outPanel);

        add(tabs);
    }
}
