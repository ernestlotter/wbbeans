package org.webbeans.dialogs;

import org.webbeans.actions.ActionListener;

/**
 * A dialog around any content component (a form), as NetBeans' {@code DialogDescriptor}: options (buttons),
 * modality, a button listener that sees every choice and {@link #setClosingOptions} deciding which choices
 * close the dialog. Use {@link DialogDisplayer#notify} for the common case, {@link DialogDisplayer#createDialog}
 * to keep a handle for {@link Dialog#hide} / {@link Dialog#show}.
 */
public class DialogDescriptor extends NotifyDescriptor {

    public static final int BOTTOM_ALIGN = 0;
    public static final int RIGHT_ALIGN = 1;

    private boolean modal;
    private int optionsAlign = BOTTOM_ALIGN;
    private ActionListener buttonListener;
    private Object[] closingOptions;
    private boolean resizable = true;

    public DialogDescriptor(Object content, String title) {
        this(content, title, true, null);
    }

    public DialogDescriptor(Object content, String title, boolean modal, ActionListener buttonListener) {
        this(content, title, modal, OK_CANCEL_OPTION, OK_OPTION, buttonListener);
    }

    public DialogDescriptor(Object content, String title, boolean modal, int optionType, Object initialValue, ActionListener buttonListener) {
        super(content, title, optionType, PLAIN_MESSAGE, null, initialValue);
        this.modal = modal;
        this.buttonListener = buttonListener;
    }

    public DialogDescriptor(Object content, String title, boolean modal, Object[] options, Object initialValue, int optionsAlign,
            ActionListener buttonListener) {
        super(content, title, DEFAULT_OPTION, PLAIN_MESSAGE, options, initialValue);
        this.modal = modal;
        this.optionsAlign = optionsAlign;
        this.buttonListener = buttonListener;
    }

    /** The NetBeans-shaped constructor: with the help topic of the dialog (a Help button opens it). */
    public DialogDescriptor(Object content, String title, boolean modal, Object[] options, Object initialValue, int optionsAlign,
            org.webbeans.ui.HelpCtx helpCtx, ActionListener buttonListener) {
        this(content, title, modal, options, initialValue, optionsAlign, buttonListener);
        setHelpCtx(helpCtx);
    }

    public boolean isModal() {
        return modal;
    }

    public void setModal(boolean modal) {
        this.modal = modal;
    }

    public int getOptionsAlign() {
        return optionsAlign;
    }

    public ActionListener getButtonListener() {
        return buttonListener;
    }

    public void setButtonListener(ActionListener l) {
        this.buttonListener = l;
    }

    /** The options that close the dialog when chosen; null (default) = all of them. */
    public Object[] getClosingOptions() {
        return closingOptions == null ? null : closingOptions.clone();
    }

    public void setClosingOptions(Object[] options) {
        this.closingOptions = options == null ? null : options.clone();
    }

    public boolean isResizable() {
        return resizable;
    }

    public void setResizable(boolean resizable) {
        this.resizable = resizable;
    }

    /** Whether choosing an option closes the dialog. */
    public boolean isClosingOption(Object option) {
        if (closingOptions == null) {
            return true;
        }
        for (Object o : closingOptions) {
            if (o == option || (o != null && o.equals(option))) {
                return true;
            }
        }
        return false;
    }

}
