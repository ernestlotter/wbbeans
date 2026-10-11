package org.webbeans.auth.spi;

import org.webbeans.auth.LoginPageModel;

/**
 * Renders the login page. Replace with {@code @ServiceProvider(service = LoginPage.class, position = 10)} for
 * branding or a different stack; the contract with the platform is only the endpoints
 * ({@code POST /login}, {@code /auth/token}, {@code /auth/<id>/start}, {@code /auth/challenge}).
 */
public interface LoginPage {

    /** @return the complete HTML document */
    String render(LoginPageModel model);
}
