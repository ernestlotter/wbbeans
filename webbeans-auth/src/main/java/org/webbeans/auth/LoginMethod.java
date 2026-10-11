package org.webbeans.auth;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the login page offers a provider: a form posted to {@code /login}, a button redirecting to
 * {@code /auth/<id>/start}, or a client-side script (vendor SDK) that obtains a token and posts it to
 * {@code /auth/token}.
 */
public final class LoginMethod {

    public enum Kind { FORM, REDIRECT, SCRIPT }

    private final Kind kind;
    private final List<LoginField> fields;
    private final String buttonLabel;
    private final String icon;
    private final String scriptResource;
    private final List<String> externalScripts;
    private final Map<String, Object> config;

    private LoginMethod(Kind kind, List<LoginField> fields, String buttonLabel, String icon, String scriptResource,
            List<String> externalScripts, Map<String, Object> config) {
        this.kind = kind;
        this.fields = Collections.unmodifiableList(fields);
        this.buttonLabel = buttonLabel;
        this.icon = icon;
        this.scriptResource = scriptResource;
        this.externalScripts = Collections.unmodifiableList(externalScripts);
        this.config = Collections.unmodifiableMap(config);
    }

    /** The standard user name / password form. */
    public static LoginMethod form() {
        return form(LoginField.text("username", "#LBL_Username"), LoginField.password("password", "#LBL_Password"));
    }

    public static LoginMethod form(LoginField... fields) {
        return new LoginMethod(Kind.FORM, Arrays.asList(fields), "#BTN_SignIn", null, null,
                Collections.<String>emptyList(), Collections.<String, Object>emptyMap());
    }

    /** A button labelled e.g. "#CTL_SignInWithGoogle" that starts the provider's redirect flow. */
    public static LoginMethod redirect(String buttonLabel, String iconResource) {
        return new LoginMethod(Kind.REDIRECT, Collections.<LoginField>emptyList(), buttonLabel, iconResource, null,
                Collections.<String>emptyList(), Collections.<String, Object>emptyMap());
    }

    /**
     * A client script (a resource path inside the provider's module) that signs the user in with a vendor SDK and
     * posts the token to {@code /auth/token}. {@code config} is exposed to it as {@code WB_AUTH_CONFIG};
     * {@code externalScripts} are URLs of SDKs to load before it (must be allowed by the CSP configuration).
     */
    public static LoginMethod script(String scriptResource, Map<String, Object> config, String... externalScripts) {
        return new LoginMethod(Kind.SCRIPT, Collections.<LoginField>emptyList(), null, null, scriptResource,
                Arrays.asList(externalScripts), new LinkedHashMap<String, Object>(config));
    }

    public LoginMethod withButtonLabel(String label) {
        return new LoginMethod(kind, fields, label, icon, scriptResource, externalScripts, config);
    }

    public Kind getKind() {
        return kind;
    }

    public List<LoginField> getFields() {
        return fields;
    }

    public String getButtonLabel() {
        return buttonLabel;
    }

    public String getIcon() {
        return icon;
    }

    public String getScriptResource() {
        return scriptResource;
    }

    public List<String> getExternalScripts() {
        return externalScripts;
    }

    public Map<String, Object> getConfig() {
        return config;
    }
}
