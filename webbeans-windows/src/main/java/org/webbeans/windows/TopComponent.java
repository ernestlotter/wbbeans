package org.webbeans.windows;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.HelpCtx;
import org.webbeans.ui.Property;
import org.webbeans.ui.Resource;
import org.webbeans.ui.components.Layout;
import org.webbeans.ui.components.Panel;

/**
 * A window of the window system: a panel that lives in a {@link Mode} as a tab, with a display name, an icon,
 * a lookup (its selection and capabilities) and the NetBeans lifecycle callbacks. Register singleton components
 * with {@link Registration} + {@link Description}; create multi-instance ones (editors) yourself and call
 * {@link #open()}.
 *
 * <pre>
 * &#64;TopComponent.Registration(mode = "explorer", openAtStartup = true, position = 100)
 * &#64;TopComponent.Description(preferredID = "FilesTC", iconBase = "org/acme/files/files.svg")
 * &#64;TopComponent.OpenActionRegistration(displayName = "#CTL_FilesAction")
 * &#64;Messages({"CTL_FilesAction=Files", "CTL_Files=Files"})
 * public final class FilesTopComponent extends TopComponent {
 *     public FilesTopComponent() {
 *         setDisplayName(Bundle.CTL_Files());
 *         setLayout(new Layout.Border());
 *         add(new TreeView(), Layout.Border.CENTER);
 *     }
 * }
 * </pre>
 *
 * All methods must be called on the session executor (they are, from event handlers and callbacks).
 */
@Renderer(id = "wb.TopComponent", resource = "org/webbeans/windows/client/topcomponent.js")
public abstract class TopComponent extends Panel implements HelpCtx.Provider {

    public static final int PERSISTENCE_ALWAYS = 0;
    public static final int PERSISTENCE_ONLY_OPENED = 1;
    public static final int PERSISTENCE_NEVER = 2;

    public static final Property<String> TC_ID = Property.of("tcId", String.class, null);
    public static final Property<String> DISPLAY_NAME = Property.of("displayName", String.class, null);
    public static final Property<String> HTML_DISPLAY_NAME = Property.of("htmlDisplayName", String.class, null);
    public static final Property<Resource> ICON = Property.of("icon", Resource.class, null);
    public static final Property<Boolean> CLOSABLE = Property.of("closable", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> BUSY = Property.of("busy", Boolean.class, Boolean.FALSE);
    public static final Property<Boolean> MODIFIED = Property.of("modified", Boolean.class, Boolean.FALSE);

    private WindowManager manager;
    private String id;
    private boolean opened;
    private boolean showing;
    private boolean active;
    private Mode mode;
    private HelpCtx helpCtx;

    protected TopComponent() {
        super("wb.TopComponent", new Layout.Fill());
    }

    // ---- identity ----------------------------------------------------------------------------------------------

    /**
     * Stable id used in layouts and by {@link WindowManager#findTopComponent}. Default: the {@code preferredID}
     * of the {@link Description}, else the simple class name.
     */
    protected String preferredID() {
        Description d = getClass().getAnnotation(Description.class);
        return d != null && !d.preferredID().isEmpty() ? d.preferredID() : getClass().getSimpleName();
    }

    /**
     * The help topic shown for F1 / Help ▸ Help Contents while this window is active: what {@link #setHelpCtx}
     * set, else the {@link Description#helpId()}, else {@link HelpCtx#DEFAULT_HELP} (the help home page).
     */
    @Override
    public HelpCtx getHelpCtx() {
        if (helpCtx != null) {
            return helpCtx;
        }
        Description d = getClass().getAnnotation(Description.class);
        return d != null && !d.helpId().isEmpty() ? new HelpCtx(d.helpId()) : HelpCtx.DEFAULT_HELP;
    }

    public final void setHelpCtx(HelpCtx ctx) {
        this.helpCtx = ctx;
    }

    /** The id in the current UI ({@code WindowManager.findTopComponentID}): {@link #preferredID()}, or {@code preferredID#n} for further instances; null until opened once. */
    public final String getTopComponentId() {
        return id;
    }

    final void setId(String id) {
        this.id = id;
        set(TC_ID, id);
    }

    final void setManager(WindowManager wm) {
        this.manager = wm;
    }

    /** The window manager of the UI this component is open in (or was created for); null before the first open. */
    public final WindowManager getWindowManager() {
        return manager;
    }

    // ---- state ---------------------------------------------------------------------------------------------------

    /** Opens into the registered mode, the mode remembered in the layout, or {@code editor}. Selects it in its mode. */
    public final void open() {
        wm().open(this, null);
    }

    public final void open(Mode mode) {
        wm().open(this, mode);
    }

    /** Asks {@link #canClose()} and closes; returns false if vetoed. */
    public final boolean close() {
        return wm().close(this, false);
    }

    /** Opens if necessary, selects the tab and makes this the activated component (focus, global context). */
    public final void requestActive() {
        wm().requestActive(this);
    }

    /** Opens if necessary and selects the tab, without changing the activated component. */
    public final void requestVisible() {
        wm().requestVisible(this);
    }

    public final boolean isOpened() {
        return opened;
    }

    /** The activated (focused) component of the window manager. */
    public final boolean isActive() {
        return active;
    }

    /** Selected in its mode (its tab is the visible one). */
    public final boolean isShowing() {
        return showing;
    }

    public final Mode getMode() {
        return mode;
    }

    private WindowManager wm() {
        WindowManager m = manager != null ? manager : WindowManager.getDefault();
        if (m == null) {
            throw new IllegalStateException("No WindowManager in the current UI");
        }
        return m;
    }

    // ---- display -----------------------------------------------------------------------------------------------

    public final void setDisplayName(String name) {
        set(DISPLAY_NAME, name);
    }

    public final String getDisplayName() {
        return get(DISPLAY_NAME);
    }

    /** A restricted HTML tab label (e.g. {@code <b>Main.java</b>}); null to use the display name. */
    public final void setHtmlDisplayName(String html) {
        set(HTML_DISPLAY_NAME, html);
    }

    public final String getHtmlDisplayName() {
        return get(HTML_DISPLAY_NAME);
    }

    public final void setIcon(Resource icon) {
        set(ICON, icon);
    }

    public final Resource getIcon() {
        return get(ICON);
    }

    public final void setToolTipText(String text) {
        setTooltip(text);
    }

    /** Whether the user may close the tab; {@link #close()} still works. Default true. */
    public final void setClosable(boolean b) {
        set(CLOSABLE, b);
    }

    public final boolean isClosable() {
        return Boolean.TRUE.equals(get(CLOSABLE));
    }

    /** Shows a spinner on the tab. */
    public final void makeBusy(boolean busy) {
        set(BUSY, busy);
    }

    /** Shows the "modified" marker on the tab (editors). */
    public final void setModified(boolean modified) {
        set(MODIFIED, modified);
    }

    public final boolean isModified() {
        return Boolean.TRUE.equals(get(MODIFIED));
    }

    // ---- persistence -------------------------------------------------------------------------------------------

    /** {@link #PERSISTENCE_ALWAYS} by default (or the {@link Description} value). */
    public int getPersistenceType() {
        Description d = getClass().getAnnotation(Description.class);
        return d != null ? d.persistenceType() : PERSISTENCE_ALWAYS;
    }

    /** Extra state saved with the layout (editors: the file, the caret). */
    protected void writeState(Map<String, Object> out) {
    }

    /** Called with the saved state before {@link #componentOpened()} when a persisted component is reopened. */
    protected void readState(Map<String, Object> in) {
    }

    // ---- lifecycle callbacks (on the session executor) ------------------------------------------------------------

    protected void componentOpened() {
    }

    protected void componentClosed() {
    }

    protected void componentShowing() {
    }

    protected void componentHidden() {
    }

    protected void componentActivated() {
    }

    protected void componentDeactivated() {
    }

    /** Veto point for user-initiated and programmatic close; confirm asynchronously and call {@link #close()} again. */
    protected boolean canClose() {
        return true;
    }

    /**
     * The session's locale changed (Options, {@code UISession.setLocale}): re-read the strings this window shows —
     * at least {@link #setDisplayName}. Called for every open window after the dock relabelled itself.
     */
    protected void localeChanged() {
    }

    final void fireLocaleChanged() {
        localeChanged();
    }

    // ---- driven by the window manager --------------------------------------------------------------------------------

    final void fireOpened(Mode m) {
        opened = true;
        mode = m;
        componentOpened();
    }

    final void fireClosed() {
        opened = false;
        showing = false;
        active = false;
        mode = null;
        componentClosed();
    }

    final void setModeInternal(Mode m) {
        mode = m;
    }

    final void fireShowing(boolean b) {
        if (showing == b) {
            return;
        }
        showing = b;
        if (b) {
            componentShowing();
        } else {
            componentHidden();
        }
    }

    final void fireActive(boolean b) {
        if (active == b) {
            return;
        }
        active = b;
        if (b) {
            componentActivated();
        } else {
            componentDeactivated();
        }
    }

    final Map<String, Object> saveState() {
        Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
        writeState(m);
        return m;
    }

    final void loadState(Map<String, Object> in) {
        readState(in);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + (id != null ? id : preferredID()) + "]";
    }

    // ---- annotations -------------------------------------------------------------------------------------------

    /**
     * Registers a singleton component: {@code Windows/Components/<preferredID>} (factory) and
     * {@code Windows/Modes/<mode>/<preferredID>} (placement). The class needs a public no-arg constructor.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    public @interface Registration {
        /** Target mode name ({@code explorer}, {@code editor}, ... or a module's own). */
        String mode();

        boolean openAtStartup() default false;

        int position() default Integer.MAX_VALUE;

        /** Only these roles see it (empty = everyone). */
        String[] roles() default {};
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Description {
        String preferredID();

        /** Icon resource path inside the module. */
        String iconBase() default "";

        int persistenceType() default PERSISTENCE_ALWAYS;

        /** The help topic of the window (a {@code @HelpPage} id); empty for the help home page. */
        String helpId() default "";

        /**
         * For multi-instance components: recreates an instance from its {@link #writeState} data at the next
         * login (a public class with a public no-arg constructor). Without one, instances are not persisted.
         */
        Class<? extends Factory> factory() default Factory.class;
    }

    /** Recreates a multi-instance component from the state it saved; return null when it cannot be restored. */
    public interface Factory {
        TopComponent create(Map<String, Object> state);
    }

    /** Generates an action that finds or creates the component and opens it: {@code Actions/Window/<id>} + {@code Menu/Window/<id>}. */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    public @interface OpenActionRegistration {
        /** {@code #KEY} into the module bundle, or a literal. */
        String displayName();

        /** Defaults to the {@link Description} preferredID. */
        String preferredID() default "";

        int position() default Integer.MAX_VALUE;
    }
}
