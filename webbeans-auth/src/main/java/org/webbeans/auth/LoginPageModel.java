package org.webbeans.auth;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.webbeans.auth.spi.AuthenticationProvider;

/** Everything a {@code LoginPage} needs to render. */
public final class LoginPageModel {

    private final List<AuthenticationProvider> providers;
    private final Challenge challenge;
    private final String errorMessage;
    private final Locale locale;
    private final String csrfToken;
    private final String returnUrl;
    private final String applicationTitle;
    private final String contextPath;

    public LoginPageModel(List<AuthenticationProvider> providers, Challenge challenge, String errorMessage, Locale locale,
            String csrfToken, String returnUrl, String applicationTitle, String contextPath) {
        this.providers = Collections.unmodifiableList(providers);
        this.challenge = challenge;
        this.errorMessage = errorMessage;
        this.locale = locale;
        this.csrfToken = csrfToken;
        this.returnUrl = returnUrl;
        this.applicationTitle = applicationTitle;
        this.contextPath = contextPath == null ? "" : contextPath;
    }

    /** Providers with a login method, in order. */
    public List<AuthenticationProvider> getProviders() {
        return providers;
    }

    /** Non-null when a multi-step login is in progress: render the challenge instead of the methods. */
    public Challenge getChallenge() {
        return challenge;
    }

    /** Localised error text, or null. */
    public String getErrorMessage() {
        return errorMessage;
    }

    public Locale getLocale() {
        return locale;
    }

    public String getCsrfToken() {
        return csrfToken;
    }

    /** Where to go after login (already validated to be a local path). */
    public String getReturnUrl() {
        return returnUrl;
    }

    public String getApplicationTitle() {
        return applicationTitle;
    }

    public String getContextPath() {
        return contextPath;
    }
}
