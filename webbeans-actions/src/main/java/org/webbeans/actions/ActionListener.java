package org.webbeans.actions;

/**
 * The simplest thing that can be registered as an action: a class implementing this, annotated with
 * {@link ActionID} and {@link ActionRegistration}. Its constructor decides the kind of action (see
 * {@link ActionRegistration}).
 */
public interface ActionListener {

    void actionPerformed(ActionEvent e);
}
