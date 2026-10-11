package org.webbeans.actions;

import org.openide.util.Lookup;

/**
 * An action whose enablement and behaviour depend on a context (the selection). The registered instance is a
 * template; presenters call {@link #createContextAwareInstance} with the UI's global context
 * ({@link Utilities#actionsGlobalContext()}) or with a popup's selection, and the returned instance tracks that
 * lookup and fires {@link #ENABLED} changes.
 */
public interface ContextAwareAction extends Action {

    Action createContextAwareInstance(Lookup context);
}
