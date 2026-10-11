package org.webbeans.dialogs;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.openide.util.Lookup;
import org.webbeans.actions.ActionEvent;
import org.webbeans.actions.ActionListener;
import org.webbeans.ui.ClickEvent;
import org.webbeans.ui.Component;
import org.webbeans.ui.Event;
import org.webbeans.ui.UI;
import org.webbeans.ui.components.Button;
import org.webbeans.ui.components.Html;
import org.webbeans.ui.components.Label;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;
import org.webbeans.ui.components.TextArea;
import org.webbeans.ui.components.TextField;

/** The dialog displayer of one UI: turns descriptors into {@link DialogComponent}s in the overlay layer. */
final class DefaultDialogDisplayer extends DialogDisplayer {

    private static final Logger LOG = Logger.getLogger(DefaultDialogDisplayer.class.getName());

    private final UI ui;

    DefaultDialogDisplayer(UI ui) {
        this.ui = ui;
    }

    @Override
    public CompletableFuture<Object> notify(NotifyDescriptor d) {
        DialogImpl dlg = new DialogImpl(d);
        dlg.show();
        return dlg.getResult();
    }

    @Override
    public Dialog createDialog(DialogDescriptor d) {
        return new DialogImpl(d);
    }

    /** One dialog instance. */
    private final class DialogImpl implements Dialog, PropertyChangeListener {
        private final NotifyDescriptor d;
        private final DialogComponent dialog = new DialogComponent();
        private final CompletableFuture<Object> result = new CompletableFuture<Object>();
        private final Map<Object, Button> buttons = new LinkedHashMap<Object, Button>();
        private TextField input;
        private WizardController wizard;
        private Button help;
        private boolean closed;

        DialogImpl(NotifyDescriptor d) {
            this.d = d;
            boolean modal = !(d instanceof DialogDescriptor) || ((DialogDescriptor) d).isModal();
            dialog.setModal(modal);
            dialog.setTitle(d.getTitle());
            dialog.setResizable(!(d instanceof DialogDescriptor) || ((DialogDescriptor) d).isResizable());
            dialog.setMessageType(messageKind(d.getMessageType()));
            dialog.setTestId("dialog");
            dialog.addCloseListener(new org.webbeans.ui.Listener<Event>() {
                @Override
                public void onEvent(Event e) {
                    close(NotifyDescriptor.CLOSED_OPTION);
                }
            });
            if (d instanceof WizardDescriptor) {
                wizard = new WizardController((WizardDescriptor<?>) d);
            } else {
                dialog.setContent(content());
            }
            buildButtons();
            applyHelp();
            applyNotification();
            d.addPropertyChangeListener(this);
        }

        /** A Help button at the left of the button bar while the descriptor has a help topic. */
        private void applyHelp() {
            boolean wanted = !d.getHelpCtx().isDefault();
            if (wanted && help == null) {
                help = new Button(org.webbeans.core.i18n.Bundles.getMessage(NotifyDescriptor.class, "CTL_Help"));
                help.setTestId("dialog-help");
                help.addClickListener(new org.webbeans.ui.Listener<ClickEvent>() {
                    @Override
                    public void onEvent(ClickEvent e) {
                        d.getHelpCtx().display();
                    }
                });
                dialog.add(help, DialogComponent.HELP);
            } else if (!wanted && help != null) {
                dialog.remove(help);
                help = null;
            }
        }

        private Component content() {
            Object m = d.getMessage();
            if (m instanceof Component) {
                return (Component) m;
            }
            if (d instanceof NotifyDescriptor.InputLine) {
                final NotifyDescriptor.InputLine il = (NotifyDescriptor.InputLine) d;
                Panel p = new Panel(new Layout.Flow(Layout.Orientation.VERTICAL, 6));
                p.add(new Label(String.valueOf(m)));
                input = new TextField(il.getInputText());
                input.setTestId("dialog-input");
                input.addInputListener(new org.webbeans.ui.Listener<TextField.InputEvent>() {
                    @Override
                    public void onEvent(TextField.InputEvent e) {
                        il.setInputText(e.getValue());
                    }
                });
                input.addChangeListener(new org.webbeans.ui.Listener<org.webbeans.ui.ChangeEvent<String>>() {
                    @Override
                    public void onEvent(org.webbeans.ui.ChangeEvent<String> e) {
                        il.setInputText(e.getValue());
                    }
                });
                p.add(input);
                return p;
            }
            if (d instanceof NotifyDescriptor.Exception) {
                Throwable t = ((NotifyDescriptor.Exception) d).getThrowable();
                Panel p = new Panel(new Layout.Flow(Layout.Orientation.VERTICAL, 6));
                p.add(new Label(String.valueOf(m)).setStyleName("wb-dialog-message"));
                if (t != null) {
                    StringWriter sw = new StringWriter();
                    t.printStackTrace(new PrintWriter(sw));
                    TextArea trace = new TextArea(sw.toString());
                    trace.setReadOnly(true);
                    trace.setMonospace(true);
                    trace.setRows(10);
                    trace.setStyleName("wb-dialog-trace");
                    p.add(trace);
                }
                return p;
            }
            return new Label(m == null ? "" : String.valueOf(m)).setStyleName("wb-dialog-message");
        }

        private void buildButtons() {
            for (Button b : buttons.values()) {
                dialog.remove(b);
            }
            buttons.clear();
            Object[] options = d.getOptions();
            Object initial = d.getInitialValue() != null ? d.getInitialValue() : options.length > 0 ? options[0] : null;
            for (final Object option : options) {
                final Button b;
                if (option instanceof Button) {
                    b = (Button) option;
                } else {
                    b = new Button(NotifyDescriptor.labelOf(option));
                }
                b.setTestId("dialog-" + String.valueOf(option instanceof NotifyDescriptor.Option ? ((NotifyDescriptor.Option) option).getKey() : b.getText())
                        .toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-"));
                if (option == initial || (initial != null && initial.equals(option))) {
                    b.setDefaultButton(true);
                    b.setKind(Button.Kind.PRIMARY);
                }
                b.addClickListener(new org.webbeans.ui.Listener<ClickEvent>() {
                    @Override
                    public void onEvent(ClickEvent e) {
                        choose(option, b);
                    }
                });
                buttons.put(option, b);
                dialog.addButton(b);
            }
            updateValidity();
        }

        private void updateValidity() {
            Object initial = d.getInitialValue();
            for (Map.Entry<Object, Button> e : buttons.entrySet()) {
                Object o = e.getKey();
                boolean affirmative = NotifyDescriptor.isAffirmative(o) || (initial != null && initial.equals(o) && o != NotifyDescriptor.CANCEL_OPTION
                        && o != NotifyDescriptor.NO_OPTION);
                if (wizard != null) {
                    affirmative = o == WizardDescriptor.NEXT_OPTION || o == WizardDescriptor.FINISH_OPTION;
                }
                if (affirmative) {
                    e.getValue().setEnabled(d.isValid() && (wizard == null || wizard.allows(o)));
                } else if (wizard != null && o == WizardDescriptor.PREVIOUS_OPTION) {
                    e.getValue().setEnabled(wizard.allows(o));
                }
            }
        }

        private void choose(Object option, Button source) {
            if (wizard != null && wizard.handle(option)) {
                return;
            }
            if (d instanceof DialogDescriptor) {
                DialogDescriptor dd = (DialogDescriptor) d;
                ActionListener l = dd.getButtonListener();
                if (l != null) {
                    try {
                        l.actionPerformed(new ActionEvent(source, Lookup.EMPTY, NotifyDescriptor.labelOf(option)));
                    } catch (RuntimeException ex) {
                        LOG.log(Level.SEVERE, "Dialog button listener failed", ex);
                    }
                }
                if (!dd.isClosingOption(option)) {
                    d.setValue(option);
                    return;
                }
            }
            close(option);
        }

        void close(Object option) {
            if (closed) {
                return;
            }
            closed = true;
            d.removePropertyChangeListener(this);
            d.setValue(option);
            if (wizard != null) {
                wizard.finished(option);
            }
            dialog.close();
            result.complete(option);
        }

        private void applyNotification() {
            NotificationLineSupport nls = d.getNotificationLineSupport();
            String[] cur = nls == null ? null : nls.current();
            if (cur == null && d instanceof WizardDescriptor) {
                Object err = ((WizardDescriptor<?>) d).getProperty(WizardDescriptor.PROP_ERROR_MESSAGE);
                cur = err == null || err.toString().isEmpty() ? null : new String[] {"error", err.toString()};
            }
            dialog.setNotification(cur == null ? null : cur[0], cur == null ? null : cur[1]);
        }

        @Override
        public void propertyChange(final PropertyChangeEvent evt) {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    String p = evt.getPropertyName();
                    if (NotifyDescriptor.PROP_VALID.equals(p)) {
                        updateValidity();
                    } else if (NotifyDescriptor.PROP_TITLE.equals(p)) {
                        dialog.setTitle(d.getTitle());
                    } else if (NotifyDescriptor.PROP_MESSAGE.equals(p) && wizard == null) {
                        dialog.setContent(content());
                    } else if (NotifyDescriptor.PROP_OPTIONS.equals(p)) {
                        buildButtons();
                    } else if (NotifyDescriptor.PROP_HELP_CTX.equals(p)) {
                        applyHelp();
                    } else if (NotifyDescriptor.PROP_INFO_NOTIFICATION.equals(p) || NotifyDescriptor.PROP_WARNING_NOTIFICATION.equals(p)
                            || NotifyDescriptor.PROP_ERROR_NOTIFICATION.equals(p)) {
                        applyNotification();
                    }
                }
            };
            if (ui.getSession().isSessionThread()) {
                r.run();
            } else {
                ui.access(r);
            }
        }

        // ---- Dialog -------------------------------------------------------------------------------------

        @Override
        public void show() {
            if (closed) {
                throw new IllegalStateException("Dialog disposed");
            }
            dialog.open(ui);
            if (input != null) {
                input.focus();
                input.selectAll();
            }
        }

        @Override
        public void hide() {
            if (dialog.isAttached()) {
                dialog.close();
            }
        }

        @Override
        public void dispose() {
            close(d.getValue());
        }

        @Override
        public void setTitle(String title) {
            d.setTitle(title);
        }

        @Override
        public String getTitle() {
            return d.getTitle();
        }

        @Override
        public boolean isVisible() {
            return dialog.isAttached();
        }

        @Override
        public CompletableFuture<Object> getResult() {
            return result;
        }

        /** Back/Next/Finish over the wizard's iterator; the step list on the side. */
        private final class WizardController implements ChangeListener {
            private final WizardDescriptor<Object> w;
            private final Html steps = new Html();
            private WizardDescriptor.Panel<Object> current;

            @SuppressWarnings("unchecked")
            WizardController(WizardDescriptor<?> w) {
                this.w = (WizardDescriptor<Object>) w;
                steps.setStyleName("wb-wizard-steps");
                dialog.add(steps, DialogComponent.SIDE);
                enter();
            }

            private void enter() {
                if (current != null) {
                    current.removeChangeListener(this);
                }
                current = w.getIterator().current();
                current.addChangeListener(this);
                current.readSettings(w.getData());
                dialog.setContent(current.getComponent());
                String[] names = w.getIterator().getStepNames();
                StringBuilder sb = new StringBuilder("<ol>");
                if (names != null) {
                    for (String n : names) {
                        boolean cur = n != null && n.equals(current.getName());
                        sb.append(cur ? "<li class=\"wb-wizard-current\">" : "<li>").append(Html.sanitize(n == null ? "" : n)).append("</li>");
                    }
                }
                steps.setHtml(sb.append("</ol>").toString());
                dialog.setTitle(d.getTitle().isEmpty() ? current.getName() : d.getTitle() + " – " + current.getName());
                updateValidity();
            }

            boolean allows(Object option) {
                if (option == WizardDescriptor.NEXT_OPTION) {
                    return w.getIterator().hasNext() && current.isValid();
                }
                if (option == WizardDescriptor.FINISH_OPTION) {
                    return !w.getIterator().hasNext() && current.isValid();
                }
                if (option == WizardDescriptor.PREVIOUS_OPTION) {
                    return w.getIterator().hasPrevious();
                }
                return true;
            }

            /** Returns true when the option was navigation (the dialog stays open). */
            @SuppressWarnings("unchecked")
            boolean handle(Object option) {
                if (option == WizardDescriptor.NEXT_OPTION) {
                    if (allows(option)) {
                        current.storeSettings(w.getData());
                        w.getIterator().nextPanel();
                        enter();
                    }
                    return true;
                }
                if (option == WizardDescriptor.PREVIOUS_OPTION) {
                    if (allows(option)) {
                        current.storeSettings(w.getData());
                        w.getIterator().previousPanel();
                        enter();
                    }
                    return true;
                }
                if (option == WizardDescriptor.FINISH_OPTION) {
                    if (!allows(option)) {
                        return true;
                    }
                    current.storeSettings(w.getData());
                    if (w.getIterator() instanceof WizardDescriptor.InstantiatingIterator) {
                        try {
                            w.setInstantiatedObjects(((WizardDescriptor.InstantiatingIterator<Object>) w.getIterator()).instantiate());
                        } catch (IOException ex) {
                            LOG.log(Level.WARNING, "Wizard instantiate failed", ex);
                            w.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, ex.getMessage() != null ? ex.getMessage() : ex.toString());
                            return true;
                        }
                    }
                }
                return false;
            }

            @SuppressWarnings("unchecked")
            void finished(Object option) {
                if (current != null) {
                    current.removeChangeListener(this);
                }
                if (w.getIterator() instanceof WizardDescriptor.InstantiatingIterator) {
                    ((WizardDescriptor.InstantiatingIterator<Object>) w.getIterator()).uninitialize(w);
                }
            }

            @Override
            public void stateChanged(ChangeEvent e) {
                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        updateValidity();
                    }
                };
                if (ui.getSession().isSessionThread()) {
                    r.run();
                } else {
                    ui.access(r);
                }
            }
        }
    }

    static String messageKind(int messageType) {
        switch (messageType) {
            case NotifyDescriptor.ERROR_MESSAGE:
                return "error";
            case NotifyDescriptor.WARNING_MESSAGE:
                return "warning";
            case NotifyDescriptor.INFORMATION_MESSAGE:
                return "info";
            case NotifyDescriptor.QUESTION_MESSAGE:
                return "question";
            default:
                return null;
        }
    }
}
