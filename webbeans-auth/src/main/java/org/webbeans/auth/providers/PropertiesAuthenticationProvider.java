package org.webbeans.auth.providers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import org.webbeans.auth.AuthContext;
import org.webbeans.auth.AuthenticationResult;
import org.webbeans.auth.Credentials;
import org.webbeans.auth.LoginMethod;
import org.webbeans.auth.PasswordCredentials;
import org.webbeans.auth.PasswordHash;
import org.webbeans.auth.Principal;
import org.webbeans.auth.spi.AuthenticationProvider;
import org.webbeans.core.files.DataDir;

/**
 * Accounts in {@code <datadir>/system/users.properties}:
 * <pre>
 * user.alice.password=pbkdf2$120000$...$...     (PasswordTool hashes passwords)
 * user.alice.roles=user,admin
 * user.alice.displayName=Alice Lotter
 * user.alice.email=alice@example.com
 * user.alice.disabled=true                      (optional)
 * </pre>
 * The file is re-read when it changes. A missing file means no accounts; the launcher creates a default
 * {@code admin} account on first start and prints its password.
 */
@ServiceProvider(service = AuthenticationProvider.class, position = 1000)
public final class PropertiesAuthenticationProvider implements AuthenticationProvider {

    private static final Logger LOG = Logger.getLogger(PropertiesAuthenticationProvider.class.getName());
    public static final String ID = "local";
    public static final String FILE_NAME = "users.properties";

    private Path file;
    private long loadedModified = -1;
    private Map<String, Account> accounts = new LinkedHashMap<String, Account>();

    static final class Account {
        String name;
        String hash;
        String[] roles = new String[0];
        String displayName;
        String email;
        boolean disabled;
    }

    public PropertiesAuthenticationProvider() {
    }

    /** For tests and embedding: a provider over an explicit file. */
    public PropertiesAuthenticationProvider(Path file) {
        this.file = file;
    }

    private synchronized Path file() {
        if (file == null) {
            DataDir dd = Lookup.getDefault().lookup(DataDir.class);
            if (dd != null) {
                file = dd.getSystemDir().resolve(FILE_NAME);
            }
        }
        return file;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "#CTL_LocalLogin";
    }

    @Override
    public boolean supports(Class<? extends Credentials> type) {
        return PasswordCredentials.class.isAssignableFrom(type);
    }

    @Override
    public LoginMethod getLoginMethod() {
        return LoginMethod.form();
    }

    @Override
    public AuthenticationResult authenticate(Credentials credentials, AuthContext context) {
        PasswordCredentials pc = (PasswordCredentials) credentials;
        Account a = load().get(pc.getUsername().toLowerCase(Locale.ROOT));
        // verify against a dummy hash when the user is unknown so timing does not reveal account existence
        boolean ok = PasswordHash.verify(pc.getPassword(), a != null ? a.hash : DUMMY_HASH) && a != null && !a.disabled;
        if (!ok) {
            return AuthenticationResult.failure("#ERR_Login");
        }
        Principal p = Principal.builder(a.name).name(a.name).displayName(a.displayName).email(a.email).roles(a.roles).build();
        return AuthenticationResult.success(p).method("password");
    }

    private static final String DUMMY_HASH = PasswordHash.hash("dummy".toCharArray(), 1000);

    private synchronized Map<String, Account> load() {
        Path f = file();
        if (f == null || !Files.exists(f)) {
            accounts = new LinkedHashMap<String, Account>();
            return accounts;
        }
        try {
            long mod = Files.getLastModifiedTime(f).toMillis();
            if (mod == loadedModified) {
                return accounts;
            }
            Properties p = new Properties();
            InputStream in = Files.newInputStream(f);
            try {
                p.load(in);
            } finally {
                in.close();
            }
            Map<String, Account> m = new LinkedHashMap<String, Account>();
            for (String key : p.stringPropertyNames()) {
                if (!key.startsWith("user.")) {
                    continue;
                }
                int dot = key.indexOf('.', 5);
                if (dot < 0) {
                    continue;
                }
                String name = key.substring(5, dot);
                String attr = key.substring(dot + 1);
                Account a = m.get(name.toLowerCase(Locale.ROOT));
                if (a == null) {
                    a = new Account();
                    a.name = name;
                    a.displayName = name;
                    m.put(name.toLowerCase(Locale.ROOT), a);
                }
                String v = p.getProperty(key).trim();
                if (attr.equals("password")) {
                    a.hash = v;
                } else if (attr.equals("roles")) {
                    a.roles = v.isEmpty() ? new String[0] : v.split("\\s*,\\s*");
                } else if (attr.equals("displayName")) {
                    a.displayName = v;
                } else if (attr.equals("email")) {
                    a.email = v;
                } else if (attr.equals("disabled")) {
                    a.disabled = Boolean.parseBoolean(v);
                }
            }
            accounts = m;
            loadedModified = mod;
            LOG.log(Level.INFO, "Loaded {0} accounts from {1}", new Object[] {m.size(), f});
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Cannot read " + f, e);
        }
        return accounts;
    }

    /** Writes or replaces an account (used by the launcher's first-start setup and PasswordTool). */
    public static synchronized void putAccount(Path file, String name, char[] password, String displayName, String... roles) throws IOException {
        Properties p = new Properties();
        if (Files.exists(file)) {
            InputStream in = Files.newInputStream(file);
            try {
                p.load(in);
            } finally {
                in.close();
            }
        }
        p.setProperty("user." + name + ".password", PasswordHash.hash(password));
        p.setProperty("user." + name + ".roles", String.join(",", Arrays.asList(roles)));
        if (displayName != null) {
            p.setProperty("user." + name + ".displayName", displayName);
        }
        Files.createDirectories(file.getParent());
        java.io.OutputStream out = Files.newOutputStream(file);
        try {
            p.store(out, "WebBeans accounts - passwords are PBKDF2 hashes; use PasswordTool to change them");
        } finally {
            out.close();
        }
    }
}
