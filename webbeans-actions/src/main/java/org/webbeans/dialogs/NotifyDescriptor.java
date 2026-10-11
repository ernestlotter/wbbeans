package org.webbeans.dialogs;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import org.webbeans.core.i18n.Bundles;

/**
 * Describes a message dialog, as NetBeans' {@code NotifyDescriptor}: a message (text or a component), a title,
 * the option set (buttons) and the message type (icon). Show it with {@link DialogDisplayer#notify}, which
 * completes a future with the option the user chose ({@link #OK_OPTION}, {@link #CANCEL_OPTION}, ... or
 * {@link #CLOSED_OPTION} when the dialog was dismissed).
 */
public class NotifyDescriptor {

    /** A standard button. */
    public static final class Option {
        private final String key;

        private Option(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        /** The localised label for the current session. */
        public String getLabel() {
            return Bundles.getMessage(NotifyDescriptor.class, "CTL_" + key);
        }

        @Override
        public String toString() {
            return key;
        }
    }

    /** A further standard option (wizards use it for Back/Next/Finish). */
    static Object option(String key) {
        return new Option(key);
    }

    public static final Object OK_OPTION = new Option("OK");
    public static final Object CANCEL_OPTION = new Option("Cancel");
    public static final Object YES_OPTION = new Option("Yes");
    public static final Object NO_OPTION = new Option("No");
    /** Returned when the dialog was dismissed (Escape, close button). */
    public static final Object CLOSED_OPTION = new Option("Closed");

    public static final int DEFAULT_OPTION = -1;
    public static final int YES_NO_OPTION = 0;
    public static final int YES_NO_CANCEL_OPTION = 1;
    public static final int OK_CANCEL_OPTION = 2;

    public static final int ERROR_MESSAGE = 0;
    public static final int INFORMATION_MESSAGE = 1;
    public static final int WARNING_MESSAGE = 2;
    public static final int QUESTION_MESSAGE = 3;
    public static final int PLAIN_MESSAGE = -1;

    public static final String PROP_MESSAGE = "message";
    public static final String PROP_TITLE = "title";
    public static final String PROP_OPTIONS = "options";
    public static final String PROP_VALID = "valid";
    public static final String PROP_VALUE = "value";
    public static final String PROP_HELP_CTX = "helpCtx";
    public static final String PROP_INFO_NOTIFICATION = "infoNotification";
    public static final String PROP_WARNING_NOTIFICATION = "warningNotification";
    public static final String PROP_ERROR_NOTIFICATION = "errorNotification";

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private Object message;
    private String title;
    private int optionType;
    private int messageType;
    private Object[] options;
    private Object initialValue;
    private Object value = CLOSED_OPTION;
    private boolean valid = true;
    private org.webbeans.ui.HelpCtx helpCtx = org.webbeans.ui.HelpCtx.DEFAULT_HELP;
    private NotificationLineSupport notificationLineSupport;

    /**
     * @param message      a String (shown as text, line breaks kept) or a {@link org.webbeans.ui.Component}
     * @param title        dialog title
     * @param optionType   {@link #DEFAULT_OPTION}, {@link #YES_NO_OPTION}, {@link #YES_NO_CANCEL_OPTION}, {@link #OK_CANCEL_OPTION}
     * @param messageType  {@link #ERROR_MESSAGE}, {@link #INFORMATION_MESSAGE}, {@link #WARNING_MESSAGE}, {@link #QUESTION_MESSAGE}, {@link #PLAIN_MESSAGE}
     * @param options      explicit buttons (standard options, Strings or {@link org.webbeans.ui.components.Button}s), or null for the option type's set
     * @param initialValue the default button, or null for the first
     */
    public NotifyDescriptor(Object message, String title, int optionType, int messageType, Object[] options, Object initialValue) {
        this.message = message;
        this.title = title;
        this.optionType = optionType;
        this.messageType = messageType;
        this.options = options == null ? null : options.clone();
        this.initialValue = initialValue;
    }

    public Object getMessage() {
        return message;
    }

    public void setMessage(Object message) {
        Object old = this.message;
        this.message = message;
        pcs.firePropertyChange(PROP_MESSAGE, old, message);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        String old = this.title;
        this.title = title;
        pcs.firePropertyChange(PROP_TITLE, old, title);
    }

    public int getOptionType() {
        return optionType;
    }

    public void setOptionType(int optionType) {
        this.optionType = optionType;
        pcs.firePropertyChange(PROP_OPTIONS, null, null);
    }

    public int getMessageType() {
        return messageType;
    }

    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }

    /** The buttons: the explicit options, or those implied by the option type. */
    public Object[] getOptions() {
        if (options != null) {
            return options.clone();
        }
        switch (optionType) {
            case YES_NO_OPTION:
                return new Object[] {YES_OPTION, NO_OPTION};
            case YES_NO_CANCEL_OPTION:
                return new Object[] {YES_OPTION, NO_OPTION, CANCEL_OPTION};
            case OK_CANCEL_OPTION:
                return new Object[] {OK_OPTION, CANCEL_OPTION};
            default:
                return new Object[] {OK_OPTION};
        }
    }

    public void setOptions(Object[] options) {
        this.options = options == null ? null : options.clone();
        pcs.firePropertyChange(PROP_OPTIONS, null, null);
    }

    /** The default button. */
    public Object getInitialValue() {
        return initialValue;
    }

    public void setInitialValue(Object initialValue) {
        this.initialValue = initialValue;
    }

    /** The option the user chose; {@link #CLOSED_OPTION} until then or when dismissed. */
    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        Object old = this.value;
        this.value = value;
        pcs.firePropertyChange(PROP_VALUE, old, value);
    }

    public boolean isValid() {
        return valid;
    }

    /** Enables or disables the affirmative buttons (OK, Yes, the default one). */
    public void setValid(boolean valid) {
        boolean old = this.valid;
        this.valid = valid;
        pcs.firePropertyChange(PROP_VALID, old, valid);
    }

    /** Creates (once) the support for info/warning/error lines under the content. */
    public NotificationLineSupport createNotificationLineSupport() {
        if (notificationLineSupport == null) {
            notificationLineSupport = new NotificationLineSupport(this);
        }
        return notificationLineSupport;
    }

    public NotificationLineSupport getNotificationLineSupport() {
        return notificationLineSupport;
    }

    /** The help topic of the dialog; a Help button appears when it is not {@link org.webbeans.ui.HelpCtx#DEFAULT_HELP}. */
    public org.webbeans.ui.HelpCtx getHelpCtx() {
        return helpCtx;
    }

    public void setHelpCtx(org.webbeans.ui.HelpCtx ctx) {
        org.webbeans.ui.HelpCtx old = helpCtx;
        helpCtx = ctx == null ? org.webbeans.ui.HelpCtx.DEFAULT_HELP : ctx;
        pcs.firePropertyChange(PROP_HELP_CTX, old, helpCtx);
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    void fire(String prop, Object oldValue, Object newValue) {
        pcs.firePropertyChange(prop, oldValue, newValue);
    }

    /** Whether the option is one of the affirmative ones that {@link #setValid} controls. */
    public static boolean isAffirmative(Object option) {
        return option == OK_OPTION || option == YES_OPTION;
    }

    /** The label of an option as a button shows it: standard options are localised, others use {@code toString}. */
    public static String labelOf(Object option) {
        if (option instanceof Option) {
            return ((Option) option).getLabel();
        }
        return option == null ? "" : option.toString();
    }

    // ---- standard subclasses -----------------------------------------------------------------------------

    /** A message with an OK button. */
    public static class Message extends NotifyDescriptor {
        public Message(Object message) {
            this(message, INFORMATION_MESSAGE);
        }

        public Message(Object message, int messageType) {
            super(message, titleFor(messageType), DEFAULT_OPTION, messageType, null, null);
        }
    }

    /** A question with Yes/No (default) or the given option set. */
    public static class Confirmation extends NotifyDescriptor {
        public Confirmation(Object message) {
            this(message, titleFor(QUESTION_MESSAGE), YES_NO_CANCEL_OPTION, QUESTION_MESSAGE);
        }

        public Confirmation(Object message, String title) {
            this(message, title, YES_NO_CANCEL_OPTION, QUESTION_MESSAGE);
        }

        public Confirmation(Object message, int optionType) {
            this(message, titleFor(QUESTION_MESSAGE), optionType, QUESTION_MESSAGE);
        }

        public Confirmation(Object message, String title, int optionType) {
            this(message, title, optionType, QUESTION_MESSAGE);
        }

        public Confirmation(Object message, String title, int optionType, int messageType) {
            super(message, title, optionType, messageType, null, null);
        }
    }

    /** A labelled text field with OK/Cancel; read the text with {@link #getInputText()} once OK was chosen. */
    public static class InputLine extends NotifyDescriptor {
        private String inputText = "";

        public InputLine(String label, String title) {
            this(label, title, OK_CANCEL_OPTION, PLAIN_MESSAGE);
        }

        public InputLine(String label, String title, int optionType, int messageType) {
            super(label, title, optionType, messageType, null, null);
        }

        public String getInputText() {
            return inputText;
        }

        public void setInputText(String text) {
            this.inputText = text == null ? "" : text;
        }
    }

    /** An error with the exception's message and, expandable, its stack trace. */
    public static class Exception extends NotifyDescriptor {
        private final Throwable throwable;

        public Exception(Throwable t) {
            this(t, t.getLocalizedMessage() != null ? t.getLocalizedMessage() : t.toString());
        }

        public Exception(Throwable t, Object message) {
            super(message, titleFor(ERROR_MESSAGE), DEFAULT_OPTION, ERROR_MESSAGE, null, null);
            this.throwable = t;
        }

        public Throwable getThrowable() {
            return throwable;
        }
    }

    static String titleFor(int messageType) {
        switch (messageType) {
            case ERROR_MESSAGE:
                return Bundles.getMessage(NotifyDescriptor.class, "TITLE_Error");
            case WARNING_MESSAGE:
                return Bundles.getMessage(NotifyDescriptor.class, "TITLE_Warning");
            case QUESTION_MESSAGE:
                return Bundles.getMessage(NotifyDescriptor.class, "TITLE_Question");
            case INFORMATION_MESSAGE:
                return Bundles.getMessage(NotifyDescriptor.class, "TITLE_Information");
            default:
                return Bundles.getMessage(NotifyDescriptor.class, "TITLE_Message");
        }
    }
}
