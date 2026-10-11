package org.webbeans.tutorial;

import org.webbeans.core.i18n.Messages;
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.Listener;
import org.webbeans.ui.components.Button;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.TextField;
import org.webbeans.windows.TopComponent;

/** Tutorial §1: a window with a text field, a button and a label. */
@TopComponent.Registration(mode = "editor", openAtStartup = true, position = 50)
@TopComponent.Description(preferredID = "HelloTC", iconBase = "org/webbeans/tutorial/hello.svg", helpId = "tutorial")
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpenHello", position = 50)
@Messages({"CTL_HelloTC=Hello", "CTL_OpenHello=Hello", "LBL_YourName=Your name", "BTN_Greet=Greet", "MSG_Hello=Hello, {0}!"})
public final class HelloTopComponent extends TopComponent {

    private final TextField name = new TextField();
    private final Label greeting = new Label();

    public HelloTopComponent() {
        setDisplayName(Bundle.CTL_HelloTC());
        setLayout(new Layout.Flow(Layout.Orientation.VERTICAL, 8));
        setPadding(12);
        name.setPlaceholder(Bundle.LBL_YourName()).setTestId("hello-name");
        Button greet = new Button(Bundle.BTN_Greet());
        greet.setKind(Button.Kind.PRIMARY).setDefaultButton(true).setTestId("hello-greet");
        greet.addClickListener(new Listener<ClickEvent>() {
            @Override
            public void onEvent(ClickEvent e) {
                greeting.setText(Bundle.MSG_Hello(name.getValue()));
            }
        });
        greeting.setTestId("hello-greeting");
        add(name);
        add(greet);
        add(greeting);
    }

    @Override
    protected void localeChanged() {
        setDisplayName(Bundle.CTL_HelloTC());
    }
}
