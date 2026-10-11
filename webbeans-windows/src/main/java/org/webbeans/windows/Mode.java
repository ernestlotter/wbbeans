package org.webbeans.windows;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/**
 * A docking slot of the window system, as in NetBeans: a tab group at a side of the main window, in the editor
 * area, or floating. Modes are declared with {@link Registration} (registry {@code Windows/Modes/<name>}); the
 * platform ships {@code explorer}, {@code navigator}, {@code editor}, {@code properties} and {@code output}.
 * Anonymous modes ({@code anonymousMode_n}) appear when the user drops a tab at the edge of another one.
 */
public interface Mode {

    enum Side { LEFT, RIGHT, TOP, BOTTOM, CENTER, FLOATING }

    /** Static properties from the registration (or defaults for anonymous modes). */
    final class Constraints {
        private final Side side;
        private final int defaultSize;
        private final boolean closeable;
        private final boolean floatable;
        private final boolean maximizable;
        private final boolean permanent;

        public Constraints(Side side, int defaultSize, boolean closeable, boolean floatable, boolean maximizable, boolean permanent) {
            this.side = side;
            this.defaultSize = defaultSize;
            this.closeable = closeable;
            this.floatable = floatable;
            this.maximizable = maximizable;
            this.permanent = permanent;
        }

        public Side getSide() {
            return side;
        }

        /** Initial width (left/right) or height (top/bottom) in pixels. */
        public int getDefaultSize() {
            return defaultSize;
        }

        /** Whether the user may close its top components. */
        public boolean isCloseable() {
            return closeable;
        }

        public boolean isFloatable() {
            return floatable;
        }

        public boolean isMaximizable() {
            return maximizable;
        }

        /** Stays in the layout even when empty (the editor area). */
        public boolean isPermanent() {
            return permanent;
        }
    }

    String getName();

    Side getSide();

    Constraints getConstraints();

    /** The top components currently docked here, in tab order. */
    List<TopComponent> getTopComponents();

    TopComponent getSelectedTopComponent();

    /** Moves (or opens) the component into this mode. */
    boolean dockInto(TopComponent tc);

    /** Whether the mode is anonymous (created by a drop) rather than registered. */
    boolean isAnonymous();

    /**
     * Declares a mode. Put it on any class of the module (a marker class is fine); a module can declare several.
     * <pre>
     * &#64;Mode.Registration(name = "tasks", side = Mode.Side.BOTTOM, defaultSize = 200)
     * public final class TasksMode {}
     * </pre>
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    @interface Registration {
        String name();

        Side side();

        int defaultSize() default 250;

        /** Order among the modes of the same side (top to bottom / left to right). */
        int position() default Integer.MAX_VALUE;

        boolean closeable() default true;

        boolean floatable() default true;

        boolean maximizable() default true;

        /** Stays visible even when empty. */
        boolean permanent() default false;

        /** Localised display name ({@code #KEY} into the module bundle), used in the Window menu. */
        String displayName() default "";
    }

    /** Several {@link Registration}s on one class. */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    @interface Registrations {
        Registration[] value();
    }
}
