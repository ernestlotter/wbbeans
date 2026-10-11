package org.webbeans.nodes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.webbeans.ui.Component;
import org.webbeans.ui.Resource;
import org.webbeans.ui.table.Cell;

/**
 * Converts a property value to and from its display text and tells the client which primitive editor to use.
 * Editing is text-based: the client sends the edited text, the server converts with {@link #fromText} and sets
 * the value; conversion errors are shown at the cell. {@link #getAsCell} lets an editor decorate the displayed
 * value (an icon, a style, HTML) — enums get a label from {@link org.webbeans.ui.EnumLabels} and their icon
 * automatically.
 */
public interface PropertyEditor<T> {

    enum Kind { TEXT, NUMBER, BOOLEAN, CHOICE, DATE, DATETIME, COLOR, MULTILINE, CUSTOM }

    /** One option of a CHOICE editor. */
    final class Choice<T> {
        private final T value;
        private final String label;
        private final Resource icon;

        public Choice(T value, String label) {
            this(value, label, null);
        }

        public Choice(T value, String label, Resource icon) {
            this.value = value;
            this.label = label;
            this.icon = icon;
        }

        public T getValue() {
            return value;
        }

        public String getLabel() {
            return label;
        }

        /** Shown next to the label in choice lists and in the cell when this choice is the value; null for none. */
        public Resource getIcon() {
            return icon;
        }
    }

    Kind getKind();

    /** Display string (localised, formatted); empty for null. */
    String getAsText(T value);

    /** Parses the display/edit text; throws {@link IllegalArgumentException} with a user-readable message. */
    T fromText(String text) throws IllegalArgumentException;

    /**
     * How the value is displayed when more than text is wanted: a {@link Cell} with icon, style or HTML; null to
     * show {@link #getAsText} as plain text (the default). Cells and sheets use it for display only — editing
     * still goes through text.
     */
    default Cell getAsCell(T value) {
        return null;
    }

    /** Options for a CHOICE editor; null otherwise. */
    default List<Choice<T>> getChoices() {
        return null;
    }

    /** Hints for the client editor: {@code min}, {@code max}, {@code step}, {@code pattern}, {@code placeholder}. */
    default Map<String, Object> getClientConfig() {
        return Collections.emptyMap();
    }

    /** For {@link Kind#CUSTOM}: a component edited in a dialog (step 5); null otherwise. */
    default Component createCustomEditor(T current) {
        return null;
    }
}
