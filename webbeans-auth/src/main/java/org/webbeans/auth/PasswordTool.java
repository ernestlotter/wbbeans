package org.webbeans.auth;

import java.io.Console;
import java.nio.file.Paths;
import java.util.Arrays;

import org.webbeans.auth.providers.PropertiesAuthenticationProvider;

/**
 * Command line: {@code java -cp app.jar org.webbeans.auth.PasswordTool <users.properties> <user> [role,...]}
 * prompts for a password and writes the account. {@code hash} as the first argument prints a hash for a password
 * read from the console.
 */
public final class PasswordTool {

    private PasswordTool() {
    }

    public static void main(String[] args) throws Exception {
        Console console = System.console();
        if (args.length >= 1 && args[0].equals("hash")) {
            char[] pw = console != null ? console.readPassword("Password: ") : readLine().toCharArray();
            System.out.println(PasswordHash.hash(pw));
            return;
        }
        if (args.length < 2) {
            System.err.println("Usage: PasswordTool <users.properties> <user> [role,role...]   |   PasswordTool hash");
            System.exit(2);
        }
        String[] roles = args.length > 2 ? args[2].split(",") : new String[] {"user"};
        char[] pw = console != null ? console.readPassword("Password for %s: ", args[1]) : readLine().toCharArray();
        PropertiesAuthenticationProvider.putAccount(Paths.get(args[0]), args[1], pw, null, roles);
        Arrays.fill(pw, '\0');
        System.out.println("Account " + args[1] + " written to " + args[0] + " with roles " + Arrays.toString(roles));
    }

    private static String readLine() throws java.io.IOException {
        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
        return r.readLine();
    }
}
