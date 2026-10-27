package org.webbeans.graph;

import java.util.List;

import org.webbeans.actions.Action;

/** Supplies the right-click menu of a widget, or of the scene background when {@code widget} is null. */
public interface PopupProvider {

    /** The actions ({@code null} entries are separators); empty or null shows nothing. */
    List<Action> getPopupActions(GraphScene<?, ?> scene, Widget widget);
}
