# Hello — the smallest WebBeans application

One jar, one action: *Tools ▸ Say Hello* opens a dialog. Two files matter:

- `pom.xml` — depends on `webbeans-actions` (the API used) and, at runtime, `webbeans-launcher` (which brings the
  rest of the platform and Jetty); puts the WebBeans annotation processors on the compiler path; copies
  `Bundle.properties` from next to the sources.
- `src/main/java/com/example/hello/HelloAction.java` — the action, registered into the menu with annotations.

There is nothing else to declare: whatever is on the class path is the application, and the processors write
the jar's contributions into `META-INF/services`.

Build the platform once (`mvn install` in the repository root, so the `org.webbeans` artifacts are in your local
repository), then:

```
mvn package exec:java            # starts http://localhost:8080/ with target/classes on the class path
```

or, outside Maven, with the platform jars that `package` copied into `target/lib`:

```
java -cp 'target/lib/*:target/hello.jar' org.webbeans.launcher.Main --datadir data --port 8080
```

On the first start an `admin` account is created and its password printed (or set `WEBBEANS_ADMIN_PASSWORD`).
Log in and open *Tools ▸ Say Hello*.

In the NetBeans IDE: *File ▸ Open Project* on this folder, then *Run* (the exec plugin's main class and
arguments are the Run configuration) — or right-click the project ▸ *Run Maven ▸ Goals…* `package exec:java`.
