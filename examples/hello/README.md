# Hello — the smallest WebBeans application

One module, one action: *Tools ▸ Say Hello* opens a dialog. Three files matter:

- `pom.xml` — depends on `webbeans-actions` (the API used) and, at runtime, `webbeans-launcher` (which brings the
  rest of the platform and Jetty); puts the WebBeans annotation processors on the compiler path; copies
  `Bundle.properties` from next to the sources; writes the module manifest into the jar.
- `src/main/resources/META-INF/MANIFEST.MF` — makes the jar a WebBeans module (`WebBeans-Module: com.example.hello`).
- `src/main/java/com/example/hello/HelloAction.java` — the action, registered into the menu with annotations.

Build the platform once (`mvn install` in the repository root, so the `org.webbeans` artifacts are in your local
repository), then:

```
mvn package exec:java            # starts http://localhost:8080/ with the module from target/classes
```

or, outside Maven, with the platform jars that `package` copied into `target/lib`:

```
java -cp 'target/lib/*:target/hello.jar' org.webbeans.launcher.Main --datadir data --port 8080
```

On the first start an `admin` account is created and its password printed (or set `WEBBEANS_ADMIN_PASSWORD`).
Log in and open *Tools ▸ Say Hello*.

In the NetBeans IDE: *File ▸ Open Project* on this folder, then *Run* (the exec plugin's main class and
arguments are the Run configuration) — or right-click the project ▸ *Run Maven ▸ Goals…* `package exec:java`.
