package org.webbeans.demo;

import org.webbeans.core.i18n.Messages;
import org.webbeans.ui.ChangeEvent;
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Size;
import org.webbeans.ui.UI;
import org.webbeans.ui.UISession;
import org.webbeans.ui.Validator;
import org.webbeans.ui.components.Button;
import org.webbeans.ui.components.CheckBox;
import org.webbeans.ui.components.ComboBox;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;
import org.webbeans.ui.components.ProgressBar;
import org.webbeans.ui.components.Spacer;
import org.webbeans.ui.components.TextArea;
import org.webbeans.ui.components.TextField;
import org.webbeans.windows.TopComponent;

/** The step-2 form, now a window in the editor area: validation, a combo box bound to objects, server timers. */
@TopComponent.Registration(mode = "editor", openAtStartup = true, position = 100)
@TopComponent.Description(preferredID = "FormTC", iconBase = "org/webbeans/demo/icons/form.svg")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenForm", position = 100)
@Messages({
    "CTL_FormTC=Form",
    "CTL_OpenForm=Form",
    "LBL_Name=Name",
    "LBL_Planet=Planet",
    "LBL_Notes=Notes",
    "LBL_Subscribe=Send me updates",
    "HINT_Name=Letters and spaces only",
    "ERR_NameRequired=A name is required",
    "ERR_NameChars=Only letters and spaces",
    "BTN_Greet=Greet",
    "BTN_Count=Count",
    "BTN_Work=Start work",
    "MSG_Hello=Hello {0} from {1}!",
    "MSG_Clicked={0} clicks",
    "MSG_Working=Working... {0}%",
    "MSG_Done=Done."
})
public final class FormTopComponent extends TopComponent {

    private final TextField name = new TextField();
    private final ComboBox<Planet> planet = new ComboBox<Planet>();
    private final Label greeting = new Label();
    private final Label counter = new Label(Bundle.MSG_Clicked(0));
    private final Button greet = new Button(Bundle.BTN_Greet());
    private final Button count = new Button(Bundle.BTN_Count());
    private final Button work = new Button(Bundle.BTN_Work());
    private final CheckBox subscribe = new CheckBox(Bundle.LBL_Subscribe());
    private final ProgressBar progress = new ProgressBar();
    private int clicks;

    public FormTopComponent() {
        setDisplayName(Bundle.CTL_FormTC());
        setLayout(new Layout.Flow(Layout.Orientation.VERTICAL, 12));
        setPadding(12);
        setScrollable(true);

        Panel form = new Panel(new Layout.Form().labelWidth(Size.px(120)));
        name.setPlaceholder("Ada Lovelace").setTestId("name");
        name.setRequired(true);
        name.setValidator(new Validator<String>() {
            @Override
            public String validate(String value) {
                if (value == null || value.trim().isEmpty()) {
                    return Bundle.ERR_NameRequired();
                }
                return value.matches("[\\p{L} ]+") ? null : Bundle.ERR_NameChars();
            }
        });
        planet.setItems(SolarSystem.get().getPlanets(), new java.util.function.Function<Planet, String>() {
            @Override
            public String apply(Planet p) {
                return p.getName();
            }
        });
        planet.setValue(SolarSystem.get().find("Earth"));
        planet.setTestId("planet");
        TextArea notes = new TextArea();
        notes.setRows(3).setTestId("notes");
        subscribe.setTestId("subscribe");
        form.add(name, Layout.Form.row(Bundle.LBL_Name(), Bundle.HINT_Name()));
        form.add(planet, Layout.Form.row(Bundle.LBL_Planet()));
        form.add(notes, Layout.Form.row(Bundle.LBL_Notes()));
        form.add(subscribe, Layout.Form.row(""));

        greeting.setTestId("greeting");
        greet.setKind(Button.Kind.PRIMARY).setDefaultButton(true).setTestId("greet");
        greet.setEnabled(false);
        greet.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                Planet p = planet.getValue();
                greeting.setText(Bundle.MSG_Hello(name.getValue(), p == null ? "?" : p.getName() + " (" + p.getGravity() + " m/s²)"));
                log("greet " + name.getValue());
            }
        });
        name.addChangeListener(new Listener<ChangeEvent<String>>() {
            @Override
            public void onEvent(ChangeEvent<String> e) {
                greet.setEnabled(e.isValid());
                log("name changed: " + e.getValue() + (e.isValid() ? "" : " (invalid)"));
            }
        });
        counter.setTestId("counter");
        count.setTestId("count");
        count.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                counter.setText(Bundle.MSG_Clicked(++clicks));
            }
        });
        progress.setTestId("progress");
        progress.setWidth(Size.px(240));
        work.setTestId("work");
        work.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                startWork();
            }
        });
        planet.addChangeListener(new Listener<ChangeEvent<Planet>>() {
            @Override
            public void onEvent(ChangeEvent<Planet> e) {
                log("planet: " + e.getValue());
            }
        });
        subscribe.addChangeListener(new Listener<ChangeEvent<Boolean>>() {
            @Override
            public void onEvent(ChangeEvent<Boolean> e) {
                log("subscribe: " + e.getValue());
            }
        });

        Panel buttons = new Panel(new Layout.Flow(Layout.Orientation.HORIZONTAL, 8));
        buttons.add(greet);
        buttons.add(count);
        buttons.add(work);
        buttons.add(new Spacer());
        buttons.add(counter);

        add(form);
        add(buttons);
        add(greeting);
        add(progress);
    }

    private void startWork() {
        final UI ui = getUI();
        work.setBusy(true).setEnabled(false);
        makeBusy(true);
        final int[] pct = {0};
        final UI.Timer[] timer = new UI.Timer[1];
        timer[0] = ui.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                pct[0] += 20;
                progress.setValue(pct[0] / 100.0).setText(Bundle.MSG_Working(pct[0]));
                if (pct[0] >= 100) {
                    progress.setText(Bundle.MSG_Done());
                    work.setBusy(false).setEnabled(true);
                    makeBusy(false);
                    timer[0].cancel();
                    log("work done");
                }
            }
        }, 150, 150);
    }

    private void log(String text) {
        UISession.current().publish(new LogLine(text));
    }

    @Override
    protected void componentOpened() {
        log("FormTC opened");
    }

    @Override
    protected void componentClosed() {
        log("FormTC closed");
    }

    @Override
    protected void componentActivated() {
        log("FormTC activated");
    }
}
