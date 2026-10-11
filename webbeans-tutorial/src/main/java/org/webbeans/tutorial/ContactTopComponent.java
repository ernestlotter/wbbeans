package org.webbeans.tutorial;

import java.util.Arrays;
import java.util.function.Consumer;

import org.webbeans.core.i18n.Messages;
import org.webbeans.dialogs.DialogDisplayer;
import org.webbeans.dialogs.NotifyDescriptor;
import org.webbeans.notifications.NotificationDisplayer;
import org.webbeans.ui.ChangeEvent;
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.Listener;
import org.webbeans.ui.Size;
import org.webbeans.ui.UI;
import org.webbeans.ui.Validator;
import org.webbeans.ui.components.Button;
import org.webbeans.ui.components.CheckBox;
import org.webbeans.ui.components.ComboBox;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;
import org.webbeans.ui.components.ProgressBar;
import org.webbeans.ui.components.Spacer;
import org.webbeans.ui.components.TextArea;
import org.webbeans.ui.components.TextField;
import org.webbeans.windows.StatusDisplayer;
import org.webbeans.windows.TopComponent;

/** Tutorial §2–§5: a validated form, a confirmation dialog, a notification and a timer-driven progress bar. */
@TopComponent.Registration(mode = "editor", openAtStartup = false, position = 60)
@TopComponent.Description(preferredID = "ContactTC")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenContact", position = 60)
@Messages({"CTL_ContactTC=New contact", "CTL_OpenContact=New contact", "LBL_Name=Name", "HINT_Name=First and last name",
    "LBL_Email=E-mail", "LBL_Role=Role", "LBL_Notes=Notes", "LBL_Newsletter=Send the newsletter", "BTN_Save=Save", "BTN_Import=Import…",
    "ERR_NameRequired=Enter a name", "ERR_Email=Not an e-mail address", "MSG_Confirm=Save {0} as a {1}?", "TITLE_Save=Save contact",
    "MSG_Saved={0} saved", "MSG_Importing=Importing… {0}%", "MSG_Imported=Import finished"})
public final class ContactTopComponent extends TopComponent {

    private final TextField name = new TextField();
    private final TextField email = new TextField();
    private final ComboBox<String> role = new ComboBox<String>();
    private final TextArea notes = new TextArea();
    private final CheckBox newsletter = new CheckBox(Bundle.LBL_Newsletter());
    private final Button save = new Button(Bundle.BTN_Save());
    private final ProgressBar progress = new ProgressBar();

    public ContactTopComponent() {
        setDisplayName(Bundle.CTL_ContactTC());
        setLayout(new Layout.Border());
        setPadding(12);

        // --- the form: label/field rows, validation on commit -----------------------------------------------------
        Panel form = new Panel(new Layout.Form().labelWidth(Size.px(110)));
        name.setRequired(true).setValidator(new Validator<String>() {
            @Override
            public String validate(String v) {
                return v == null || v.trim().isEmpty() ? Bundle.ERR_NameRequired() : null;
            }
        });
        email.setValidator(new Validator<String>() {
            @Override
            public String validate(String v) {
                return v == null || v.isEmpty() || v.matches("[^@\\s]+@[^@\\s]+") ? null : Bundle.ERR_Email();
            }
        });
        role.setItems(Arrays.asList("customer", "supplier", "partner")).setAllowEmpty(false).setValue("customer");
        notes.setRows(3);
        form.add(name, Layout.Form.row(Bundle.LBL_Name(), Bundle.HINT_Name()));
        form.add(email, Layout.Form.row(Bundle.LBL_Email()));
        form.add(role, Layout.Form.row(Bundle.LBL_Role()));
        form.add(notes, Layout.Form.row(Bundle.LBL_Notes()));
        form.add(newsletter, Layout.Form.row(""));

        // the Save button follows the validity of the name field
        save.setKind(Button.Kind.PRIMARY).setDefaultButton(true).setEnabled(false);
        name.addChangeListener(new Listener<ChangeEvent<String>>() {
            @Override
            public void onEvent(ChangeEvent<String> e) {
                save.setEnabled(e.isValid());
            }
        });
        save.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                confirmAndSave();
            }
        });
        Button importButton = new Button(Bundle.BTN_Import());
        importButton.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                startImport();
            }
        });

        // --- a button row: Spacer pushes the progress bar to the right -------------------------------------------
        Panel buttons = new Panel(new Layout.Flow(Layout.Orientation.HORIZONTAL, 8));
        buttons.add(save);
        buttons.add(importButton);
        buttons.add(new Spacer());
        progress.setWidth(Size.px(200)).setVisible(false);
        buttons.add(progress);

        add(form, Layout.Border.CENTER);
        add(buttons, Layout.Border.SOUTH);
    }

    /** Dialogs are asynchronous: the answer arrives in the future, on the session thread. */
    private void confirmAndSave() {
        final String who = name.getValue().trim();
        NotifyDescriptor.Confirmation ask = new NotifyDescriptor.Confirmation(Bundle.MSG_Confirm(who, role.getValue()),
                Bundle.TITLE_Save(), NotifyDescriptor.YES_NO_OPTION);
        DialogDisplayer.getDefault().notify(ask).thenAccept(new Consumer<Object>() {
            @Override
            public void accept(Object answer) {
                if (answer == NotifyDescriptor.YES_OPTION) {
                    StatusDisplayer.getDefault().setStatusText(Bundle.MSG_Saved(who));
                    NotificationDisplayer.getDefault().notify(Bundle.TITLE_Save(), Bundle.MSG_Saved(who), NotificationDisplayer.Category.INFO);
                    setModified(false);
                }
            }
        });
    }

    /** A timer runs on the session thread: it may touch components directly. */
    private void startImport() {
        progress.setVisible(true);
        progress.setValue(0).setText(Bundle.MSG_Importing(0));
        makeBusy(true);
        final int[] pct = {0};
        final UI.Timer[] timer = new UI.Timer[1];
        timer[0] = getUI().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                pct[0] += 25;
                progress.setValue(pct[0] / 100.0).setText(Bundle.MSG_Importing(pct[0]));
                if (pct[0] >= 100) {
                    timer[0].cancel();
                    makeBusy(false);
                    progress.setText(Bundle.MSG_Imported());
                }
            }
        }, 200, 200);
    }

    @Override
    protected void localeChanged() {
        setDisplayName(Bundle.CTL_ContactTC());
    }
}
