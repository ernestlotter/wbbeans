package org.webbeans.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.webbeans.auth.providers.PropertiesAuthenticationProvider;
import org.webbeans.core.Platform;
import org.webbeans.server.ServerConfig;
import org.webbeans.server.WbServer;

/**
 * Starts the platform and the HTTP server.
 *
 * <pre>
 * java -cp 'lib/*' org.webbeans.launcher.Main [--port 8080] [--host 0.0.0.0] [--datadir data] [--modules modules]
 *      [--context /app] [--title "My App"] [--locale de] [--session-timeout 30m] [--dev] [--admin-password x]
 *      [--no-websocket] [--http-threads 32] [--max-upload 256m]
 * </pre>
 *
 * On the first start with no {@code users.properties} an {@code admin} account is created; its password is taken
 * from {@code --admin-password} or the {@code WEBBEANS_ADMIN_PASSWORD} environment variable, otherwise generated
 * and printed once.
 */
public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        ServerConfig config = new ServerConfig();
        Path dataDir = Paths.get("data");
        File modulesDir = null;
        String adminPassword = System.getenv("WEBBEANS_ADMIN_PASSWORD");
        boolean noDefaultAdmin = false;
        String branding = null;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            String v = i + 1 < args.length ? args[i + 1] : null;
            if (a.equals("--port")) {
                config.port(Integer.parseInt(require(a, v)));
                i++;
            } else if (a.equals("--host")) {
                config.host(require(a, v));
                i++;
            } else if (a.equals("--datadir")) {
                dataDir = Paths.get(require(a, v));
                i++;
            } else if (a.equals("--modules")) {
                modulesDir = new File(require(a, v));
                i++;
            } else if (a.equals("--context")) {
                config.contextPath(require(a, v));
                i++;
            } else if (a.equals("--title")) {
                config.applicationTitle(require(a, v));
                i++;
            } else if (a.equals("--locale")) {
                Locale.setDefault(Locale.forLanguageTag(require(a, v)));
                i++;
            } else if (a.equals("--session-timeout")) {
                config.sessionIdleTimeout(parseDuration(require(a, v)));
                i++;
            } else if (a.equals("--admin-password")) {
                adminPassword = require(a, v);
                i++;
            } else if (a.equals("--no-default-admin")) {
                noDefaultAdmin = true;
            } else if (a.equals("--branding")) {
                branding = require(a, v);
                i++;
            } else if (a.equals("--dev")) {
                config.devMode(true);
            } else if (a.equals("--secure-cookies")) {
                config.secureCookies(true);
            } else if (a.equals("--trusted-proxy")) {
                config.trustedProxy(require(a, v));
                i++;
            } else if (a.equals("--no-websocket")) {
                config.webSocketEnabled(false);   // long polling only (a proxy that cannot upgrade)
            } else if (a.equals("--http-threads")) {
                config.httpThreads(Integer.parseInt(require(a, v)));
                i++;
            } else if (a.equals("--max-upload")) {
                config.maxUploadSize(parseSize(require(a, v)));   // 10m, 2g, 512k, or bytes
                i++;
            } else if (a.equals("--help") || a.equals("-h")) {
                System.out.println("Options: --port N --host H --datadir DIR --modules DIR --context /path --title T --locale xx"
                        + " --session-timeout 30m --admin-password PW --no-default-admin --branding NAME --dev --secure-cookies --trusted-proxy IP"
                        + " --no-websocket --http-threads N --max-upload 256m");
                return;
            } else {
                throw new IllegalArgumentException("Unknown option " + a);
            }
        }

        if (branding != null && !branding.isEmpty()) {
            org.openide.util.NbBundle.setBranding(branding);   // Bundle_<branding>.properties overlays every bundle
        }
        Platform.Config pc = new Platform.Config().dataDir(dataDir);
        File embedded = EmbeddedModules.extract(dataDir);
        if (modulesDir != null) {
            pc.moduleDir(modulesDir);
            if (embedded != null) {
                EmbeddedModules.link(embedded, modulesDir);
            }
        } else if (embedded != null) {
            pc.moduleDir(embedded);
        }
        final Platform platform = Platform.start(pc);
        if (!noDefaultAdmin) {
            ensureAdminAccount(platform, adminPassword);
        }

        final WbServer server = new WbServer(config);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread("wb-shutdown") {
            @Override
            public void run() {
                server.stop();
                platform.shutdown(true);
            }
        });
        LOG.log(Level.INFO, "Ready: http://localhost:{0}{1}/", new Object[] {String.valueOf(server.getPort()), config.getContextPath()});
        Thread.currentThread().join();
    }

    /** {@code 512k}, {@code 10m}, {@code 2g} or plain bytes. */
    static long parseSize(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        long mult = 1;
        if (t.endsWith("k")) {
            mult = 1024L;
        } else if (t.endsWith("m")) {
            mult = 1024L * 1024;
        } else if (t.endsWith("g")) {
            mult = 1024L * 1024 * 1024;
        }
        if (mult > 1) {
            t = t.substring(0, t.length() - 1);
        }
        return Long.parseLong(t.trim()) * mult;
    }

    private static String require(String option, String value) {
        if (value == null) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return value;
    }

    static Duration parseDuration(String s) {
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(s.substring(0, s.length() - 2)));
        }
        char unit = s.charAt(s.length() - 1);
        long n = Long.parseLong(Character.isDigit(unit) ? s : s.substring(0, s.length() - 1));
        switch (unit) {
            case 's': return Duration.ofSeconds(n);
            case 'h': return Duration.ofHours(n);
            case 'd': return Duration.ofDays(n);
            default: return Duration.ofMinutes(n);
        }
    }

    static void ensureAdminAccount(Platform platform, String password) throws IOException {
        Path users = platform.getDataDir().getSystemDir().resolve(PropertiesAuthenticationProvider.FILE_NAME);
        if (Files.exists(users)) {
            return;
        }
        boolean generated = password == null || password.isEmpty();
        if (generated) {
            password = randomPassword();
        }
        PropertiesAuthenticationProvider.putAccount(users, "admin", password.toCharArray(), "Administrator", "admin", "user");
        if (generated) {
            System.out.println("==============================================================");
            System.out.println(" Created account 'admin' with password: " + password);
            System.out.println(" (" + users + ")");
            System.out.println("==============================================================");
        } else {
            LOG.log(Level.INFO, "Created account ''admin'' in {0}", users);
        }
    }

    private static String randomPassword() {
        String alphabet = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 14; i++) {
            sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
