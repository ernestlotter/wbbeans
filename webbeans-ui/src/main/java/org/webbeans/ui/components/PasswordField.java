package org.webbeans.ui.components;

import java.util.Arrays;
import java.util.List;

import org.webbeans.core.i18n.Bundles;
import org.webbeans.core.registry.Renderer;
import org.webbeans.ui.Property;
import org.webbeans.ui.ValueComponent;

/**
 * A {@link TextField} for passwords: masked, with an optional reveal button and strength meter. The meter is
 * drawn by the client from {@link #strength}'s rules (length, character classes); the server exposes the same
 * score for validation.
 */
@Renderer(id = "wb.PasswordField", resource = "org/webbeans/ui/client/renderers/widgets.js", css = "org/webbeans/ui/client/renderers/widgets.css")
public class PasswordField extends TextField {

    public enum Strength { WEAK, FAIR, GOOD, STRONG }

    public static final Property<Boolean> REVEAL_BUTTON = Property.of("revealButton", Boolean.class, Boolean.TRUE);
    public static final Property<Boolean> STRENGTH_METER = Property.of("strengthMeter", Boolean.class, Boolean.FALSE);
    @SuppressWarnings("unchecked")
    static final Property<List<String>> STRENGTH_LABELS = (Property<List<String>>) (Property<?>) Property.of("strengthLabels", List.class, null);

    public PasswordField() {
        super("wb.PasswordField", VALUE);
        set(PASSWORD, Boolean.TRUE);
        set(STRENGTH_LABELS, Arrays.asList(Bundles.getMessage(ValueComponent.class, "LBL_PasswordWeak"), Bundles.getMessage(ValueComponent.class, "LBL_PasswordFair"),
                Bundles.getMessage(ValueComponent.class, "LBL_PasswordGood"), Bundles.getMessage(ValueComponent.class, "LBL_PasswordStrong")));
    }

    public PasswordField setRevealButton(boolean b) {
        set(REVEAL_BUTTON, b);
        return this;
    }

    public PasswordField setStrengthMeter(boolean b) {
        set(STRENGTH_METER, b);
        return this;
    }

    /** The strength of the current value. */
    public Strength getStrength() {
        return strength(getValue());
    }

    /** Length and character-class based score; the client's meter uses the same rules. */
    public static Strength strength(String pw) {
        if (pw == null) {
            return Strength.WEAK;
        }
        int score = 0;
        if (pw.length() >= 8) {
            score++;
        }
        if (pw.length() >= 12) {
            score++;
        }
        boolean lower = false, upper = false, digit = false, other = false;
        for (int i = 0; i < pw.length(); i++) {
            char c = pw.charAt(i);
            if (Character.isLowerCase(c)) {
                lower = true;
            } else if (Character.isUpperCase(c)) {
                upper = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            } else {
                other = true;
            }
        }
        int classes = (lower ? 1 : 0) + (upper ? 1 : 0) + (digit ? 1 : 0) + (other ? 1 : 0);
        score += classes >= 2 ? 1 : 0;
        score += classes >= 4 ? 1 : 0;
        if (pw.length() < 6) {
            score = 0;
        }
        return score <= 1 ? Strength.WEAK : score == 2 ? Strength.FAIR : score == 3 ? Strength.GOOD : Strength.STRONG;
    }
}
