package org.webbeans.dialogs;

/** Shows an info, warning or error line under a dialog's content ({@link NotifyDescriptor#createNotificationLineSupport}). */
public final class NotificationLineSupport {

    private final NotifyDescriptor descriptor;
    private String info;
    private String warning;
    private String error;

    NotificationLineSupport(NotifyDescriptor d) {
        this.descriptor = d;
    }

    public void setInformationMessage(String msg) {
        info = msg;
        descriptor.fire(NotifyDescriptor.PROP_INFO_NOTIFICATION, null, msg);
    }

    public void setWarningMessage(String msg) {
        warning = msg;
        descriptor.fire(NotifyDescriptor.PROP_WARNING_NOTIFICATION, null, msg);
    }

    public void setErrorMessage(String msg) {
        error = msg;
        descriptor.fire(NotifyDescriptor.PROP_ERROR_NOTIFICATION, null, msg);
    }

    public void clearMessages() {
        info = warning = error = null;
        descriptor.fire(NotifyDescriptor.PROP_INFO_NOTIFICATION, null, null);
    }

    public String getInformationMessage() {
        return info;
    }

    public String getWarningMessage() {
        return warning;
    }

    public String getErrorMessage() {
        return error;
    }

    /** The message to show (error wins over warning over info) and its kind, or null. */
    String[] current() {
        if (error != null && !error.isEmpty()) {
            return new String[] {"error", error};
        }
        if (warning != null && !warning.isEmpty()) {
            return new String[] {"warning", warning};
        }
        if (info != null && !info.isEmpty()) {
            return new String[] {"info", info};
        }
        return null;
    }
}
