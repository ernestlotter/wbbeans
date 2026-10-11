package org.webbeans.auth;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** One input of a login form or challenge. Labels may be {@code #KEY} bundle references. */
public final class LoginField {

    public enum Kind { TEXT, PASSWORD, CHOICE, CHECKBOX, HIDDEN }

    private final String name;
    private final String label;
    private final Kind kind;
    private final boolean required;
    private final List<String> choices;
    private final String defaultValue;

    private LoginField(String name, String label, Kind kind, boolean required, List<String> choices, String defaultValue) {
        this.name = name;
        this.label = label;
        this.kind = kind;
        this.required = required;
        this.choices = choices;
        this.defaultValue = defaultValue;
    }

    public static LoginField text(String name, String label) {
        return new LoginField(name, label, Kind.TEXT, true, Collections.<String>emptyList(), null);
    }

    public static LoginField password(String name, String label) {
        return new LoginField(name, label, Kind.PASSWORD, true, Collections.<String>emptyList(), null);
    }

    public static LoginField choice(String name, String label, String... choices) {
        return new LoginField(name, label, Kind.CHOICE, true, Arrays.asList(choices), choices.length > 0 ? choices[0] : null);
    }

    public static LoginField checkbox(String name, String label) {
        return new LoginField(name, label, Kind.CHECKBOX, false, Collections.<String>emptyList(), "false");
    }

    public static LoginField hidden(String name, String value) {
        return new LoginField(name, "", Kind.HIDDEN, false, Collections.<String>emptyList(), value);
    }

    public LoginField optional() {
        return new LoginField(name, label, kind, false, choices, defaultValue);
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isRequired() {
        return required;
    }

    public List<String> getChoices() {
        return choices;
    }

    public String getDefaultValue() {
        return defaultValue;
    }
}
