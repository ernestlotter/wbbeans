package org.webbeans.options;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import org.openide.util.Lookup;
import org.webbeans.ui.Component;

/**
 * One category of the Options dialog (Tools ▸ Options): a component that edits settings, usually stored with
 * {@link WbPreferences}. Register it with {@link TopLevelRegistration}; the dialog creates a fresh controller per
 * opening, calls {@link #update()} before showing, {@link #applyChanges()} on OK/Apply and {@link #cancel()}
 * otherwise. Call {@link #changed()} whenever the component's state changes so the dialog can enable Apply and
 * re-check {@link #isValid()}.
 *
 * <pre>
 * &#64;OptionsPanelController.TopLevelRegistration(categoryName = "#CTL_Acme", iconBase = "com/acme/options.svg", position = 500)
 * public final class AcmeOptions extends OptionsPanelController { ... }
 * </pre>
 */
public abstract class OptionsPanelController {

    /** Registers the controller under {@code OptionsDialog/<id>}. */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    public @interface TopLevelRegistration {
        /** Entry name; defaults to the class' simple name. */
        String id() default "";

        /** Display name, or a {@code #key} of the package bundle. */
        String categoryName();

        String iconBase() default "";

        /** Search keywords (or a {@code #key}). */
        String keywords() default "";

        int position() default Integer.MAX_VALUE;

        String[] roles() default {};
    }

    private final List<Runnable> listeners = new ArrayList<Runnable>();

    /** Creates (once per dialog) the component editing the settings; {@code master} is the dialog's lookup. */
    public abstract Component getComponent(Lookup master);

    /** Loads the stored values into the component. */
    public abstract void update();

    /** Stores the component's values. */
    public abstract void applyChanges();

    /** Discards the component's values. */
    public abstract void cancel();

    public abstract boolean isValid();

    public abstract boolean isChanged();

    /** Help id, or null. */
    public String getHelpId() {
        return null;
    }

    public final void addChangeListener(Runnable r) {
        listeners.add(r);
    }

    public final void removeChangeListener(Runnable r) {
        listeners.remove(r);
    }

    /** Subclasses call this when the component's state changed. */
    protected final void changed() {
        for (Runnable r : new ArrayList<Runnable>(listeners)) {
            r.run();
        }
    }
}
