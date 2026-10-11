package org.webbeans.auth.providers;

import java.util.Arrays;

import org.webbeans.auth.AuthContext;
import org.webbeans.auth.AuthenticationResult;
import org.webbeans.auth.Credentials;
import org.webbeans.auth.HeaderCredentials;
import org.webbeans.auth.Principal;
import org.webbeans.auth.spi.AuthenticationProvider;

/**
 * Trusts identity headers set by a reverse proxy that already authenticated the user (SAML, Kerberos, mTLS).
 * Only requests {@link HeaderCredentials#isFromTrustedProxy() from a trusted proxy} are accepted; configure
 * {@code --trusted-proxies}. Not registered by default: an application enables it with a {@code @ServiceProvider}
 * subclass or by configuring the header names and registering an instance.
 */
public class HeaderAuthenticationProvider implements AuthenticationProvider {

    public static final String ID = "header";

    private final String userHeader;
    private final String rolesHeader;
    private final String displayNameHeader;
    private final String emailHeader;

    public HeaderAuthenticationProvider() {
        this("X-Remote-User", "X-Remote-Groups", "X-Remote-Name", "X-Remote-Email");
    }

    public HeaderAuthenticationProvider(String userHeader, String rolesHeader, String displayNameHeader, String emailHeader) {
        this.userHeader = userHeader;
        this.rolesHeader = rolesHeader;
        this.displayNameHeader = displayNameHeader;
        this.emailHeader = emailHeader;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "#CTL_HeaderLogin";
    }

    @Override
    public boolean supports(Class<? extends Credentials> type) {
        return HeaderCredentials.class.isAssignableFrom(type);
    }

    @Override
    public AuthenticationResult authenticate(Credentials credentials, AuthContext context) {
        HeaderCredentials hc = (HeaderCredentials) credentials;
        if (!hc.isFromTrustedProxy()) {
            return AuthenticationResult.notMine();
        }
        String user = hc.getHeader(userHeader);
        if (user == null || user.trim().isEmpty()) {
            return AuthenticationResult.notMine();
        }
        Principal.Builder b = Principal.builder(user.trim()).name(user.trim());
        String groups = hc.getHeader(rolesHeader);
        if (groups != null && !groups.trim().isEmpty()) {
            b.roles(Arrays.asList(groups.split("\\s*[,;]\\s*")));
        }
        String dn = hc.getHeader(displayNameHeader);
        if (dn != null) {
            b.displayName(dn);
        }
        String email = hc.getHeader(emailHeader);
        if (email != null) {
            b.email(email);
        }
        return AuthenticationResult.success(b.build()).method("header");
    }
}
