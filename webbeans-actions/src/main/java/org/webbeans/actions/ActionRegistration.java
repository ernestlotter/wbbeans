package org.webbeans.actions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers a class as an action in the registry (with {@link ActionID}). The class is either an {@link Action}
 * (public no-arg constructor; used as is) or an {@link ActionListener} whose public constructor picks the kind,
 * exactly as NetBeans does:
 * <ul>
 * <li>no-arg constructor → always enabled ({@link Actions#alwaysEnabled});</li>
 * <li>{@code (T context)} → enabled when exactly one {@code T} is in the context ({@link Actions#context});</li>
 * <li>{@code (List<T> context)} → enabled when at least one {@code T} is in the context.</li>
 * </ul>
 * With {@link #key()} the action is a callback: it delegates to the {@link ActionMap} entry of that name found in
 * the context (the active window's map); the class is the fallback when the map has none.
 *
 * <pre>
 * &#64;ActionID(category = "Planets", id = "org.webbeans.demo.ExploreAction")
 * &#64;ActionRegistration(displayName = "#CTL_Explore", iconBase = "org/webbeans/demo/explore.svg")
 * &#64;ActionReferences({
 *     &#64;ActionReference(path = "Menu/Planets", position = 100),
 *     &#64;ActionReference(path = "Toolbars/Planets", position = 100),
 *     &#64;ActionReference(path = "Shortcuts", name = "D-E")})
 * public final class ExploreAction implements ActionListener {
 *     private final Planet planet;
 *     public ExploreAction(Planet p) { planet = p; }
 *     public void actionPerformed(ActionEvent e) { ... }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface ActionRegistration {

    /** {@code #KEY} into the module bundle, or a literal. */
    String displayName();

    /** Icon resource path (optional). */
    String iconBase() default "";

    /** The class is loaded and instantiated on first use (menus render from the registry attributes). */
    boolean lazy() default true;

    /** {@code actionPerformed} runs on a background thread with a progress handle; UI access needs {@code ui.access}. */
    boolean asynchronous() default false;

    /** Keeps the last non-empty selection when the focus moves to a window without one. */
    boolean surviveFocusChange() default false;

    /** For callback actions: the {@link ActionMap} key to delegate to. */
    String key() default "";

    /** Different text in menus (optional). */
    String menuText() default "";

    /** Different text in popups (optional). */
    String popupText() default "";

    /** Menus show no icon. */
    boolean noIconInMenu() default false;

    /** Roles that may see and invoke the action; empty = everyone. */
    String[] roles() default {};
}
