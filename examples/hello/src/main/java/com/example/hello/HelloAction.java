package com.example.hello;

import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionID;
import org.webbeans.actions.ActionListener;
import org.webbeans.actions.ActionReference;
import org.webbeans.actions.ActionRegistration;
import org.webbeans.core.i18n.Messages;
import org.webbeans.dialogs.DialogDisplayer;
import org.webbeans.dialogs.NotifyDescriptor;

/**
 * One always-enabled action in the main menu (Tools ▸ Say Hello) that opens a message dialog.
 * The annotations are turned into registry entries at compile time by the WebBeans annotation processors;
 * {@code @Messages} generates the {@code Bundle} class (and {@code Bundle.properties}) in this package.
 */
@ActionID(category = "Hello", id = "com.example.hello.HelloAction")
@ActionRegistration(displayName = "#CTL_SayHello")
@ActionReference(path = "Menu/Tools", position = 100)
@Messages({"CTL_SayHello=Say Hello", "MSG_Hello=Hello from WebBeans!"})
public final class HelloAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(Bundle.MSG_Hello()));
    }
}
