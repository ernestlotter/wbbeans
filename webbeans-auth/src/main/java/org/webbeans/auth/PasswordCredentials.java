package org.webbeans.auth;

import java.util.Arrays;

/** A user name and password posted to the login form (or a programmatic login). */
public final class PasswordCredentials implements Credentials {

    private final String username;
    private final char[] password;

    public PasswordCredentials(String username, char[] password) {
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? new char[0] : password.clone();
    }

    public PasswordCredentials(String username, String password) {
        this(username, password == null ? null : password.toCharArray());
    }

    public String getUsername() {
        return username;
    }

    /** The caller's view of the password; cleared by {@link #clear()} after authentication. */
    public char[] getPassword() {
        return password;
    }

    public void clear() {
        Arrays.fill(password, '\0');
    }

    @Override
    public String toString() {
        return "PasswordCredentials[" + username + "]";
    }
}
