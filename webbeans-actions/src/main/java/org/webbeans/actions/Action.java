package org.webbeans.actions;

import java.beans.PropertyChangeListener;

import org.webbeans.ui.Resource;

/**
 * Something the user can invoke: a menu item, a toolbar button, a node's popup entry, a shortcut. Modelled on
 * {@code javax.swing.Action} without Swing: a value map ({@link #NAME}, {@link #ICON}, ...), an enabled flag
 * and property change events that presenters listen to. {@code null} in an action list means a separator.
 *
 * <p>Most actions extend {@link AbstractAction} or are created by {@link Actions} from a registration
 * ({@link ActionRegistration}); {@link ContextAwareAction} is what selection-sensitive actions implement.
 */
public interface Action {

    /** Display name (String). */
    String NAME = "name";
    /** Icon ({@link Resource}). */
    String ICON = "icon";
    /** Tooltip / status line text (String). */
    String SHORT_DESCRIPTION = "shortDescription";
    /** Keyboard shortcut in client syntax, e.g. {@code Ctrl+Shift+S} (String); shown in menus. */
    String ACCELERATOR = "accelerator";
    /** Enabled flag (Boolean); fired by {@link #setEnabled}. */
    String ENABLED = "enabled";
    /** Checked state of a checkbox/radio item (Boolean, or null for plain items). */
    String SELECTED = "selected";
    /** Runs on a background thread when TRUE (Boolean); see {@link ActionRegistration#asynchronous()}. */
    String ASYNC = "async";
    /** Menus show no icon for this action when TRUE (Boolean). */
    String NO_ICON_IN_MENU = "noIconInMenu";
    /** Text used in menus instead of {@link #NAME} (String), as NetBeans' {@code menuText}. */
    String MENU_TEXT = "menuText";
    /** Text used in popups instead of {@link #NAME} (String), as NetBeans' {@code popupText}. */
    String POPUP_TEXT = "popupText";

    /** Localised label ({@link #NAME}). */
    String getDisplayName();

    /** Icon resource ({@link #ICON}), or null. */
    Resource getIcon();

    /** Whether it may be invoked now (menus render disabled entries). */
    boolean isEnabled();

    void setEnabled(boolean enabled);

    /**
     * Performs the action on the session executor.
     *
     * @param e the invocation: its context lookup (the selection), the presenter component and the UI
     */
    void actionPerformed(ActionEvent e);

    Object getValue(String key);

    void putValue(String key, Object value);

    void addPropertyChangeListener(PropertyChangeListener l);

    void removePropertyChangeListener(PropertyChangeListener l);
}
