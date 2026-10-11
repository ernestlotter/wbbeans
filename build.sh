#!/usr/bin/env bash
# Offline build for environments without Maven Central access.
# Compiles every module with javac --release 8 against the jars in $LIB and runs the JUnit tests.
# The canonical build is `mvn verify`; this script mirrors it module by module.
set -euo pipefail
cd "$(dirname "$0")"
LIB="${LIB:-$(pwd)/../third_party/lib}"
OUT="${OUT:-$(pwd)/build}"
CP_BASE="$LIB/org-openide-util-lookup.jar:$LIB/org-openide-util.jar"
CP_TEST="$LIB/junit-4.13.2.jar:$LIB/hamcrest-core-1.3.jar"
# Jetty 9.4 (the last Java 8 line) + its WebSocket modules + the Servlet 3.1 API: the HTTP front end
JETTY_VERSION="${JETTY_VERSION:-9.4.58.v20250814}"
JETTY_JARS="javax.servlet-api-3.1.0.jar"
for j in jetty-util jetty-jmx jetty-io jetty-http jetty-server jetty-security jetty-util-ajax jetty-servlet websocket-api websocket-common websocket-servlet websocket-server; do JETTY_JARS="$JETTY_JARS $j-$JETTY_VERSION.jar"; done
JETTY_CP=$(for j in $JETTY_JARS; do printf '%s:' "$LIB/$j"; done); JETTY_CP="${JETTY_CP%:}"
# the Jetty WebSocket client, used by the server tests only
JETTY_CLIENT_CP="$LIB/jetty-xml-$JETTY_VERSION.jar:$LIB/jetty-client-$JETTY_VERSION.jar:$LIB/websocket-client-$JETTY_VERSION.jar"
JAVAC="javac --release 8 -Xlint:all,-options,-processing,-this-escape -Werror -encoding UTF-8"

build_module() {   # name  classpath  extra-javac-flags
  local name=$1 cp=$2 flags=${3:-}
  local src="$name/src/main/java" res="$name/src/main/resources" out="$OUT/$name/classes"
  local gen="$OUT/$name/generated-sources"
  rm -rf "$out" "$gen"; mkdir -p "$out" "$gen"
  echo "== compiling $name"
  # resources first: the annotation processor may write merged Bundle.properties over the copies
  [ -d "$res" ] && cp -r "$res/." "$out/"
  (cd "$src" && find . -type f ! -name '*.java' -exec cp --parents {} "$out/" \;)
  # shellcheck disable=SC2086
  $JAVAC $flags -sourcepath "$src" -cp "$cp" -d "$out" -s "$gen" $(find "$src" -name '*.java')
  if [ -f "$res/META-INF/MANIFEST.MF" ]; then
    (cd "$out" && jar cfm "$OUT/$name.jar" META-INF/MANIFEST.MF .)
  else
    (cd "$out" && jar cf "$OUT/$name.jar" .)
  fi
}

test_module() {    # name  classpath  extra-javac-flags
  local name=$1 cp=$2 flags=${3:-}
  local src="$name/src/test/java" res="$name/src/test/resources" out="$OUT/$name/test-classes"
  [ -d "$src" ] || return 0
  rm -rf "$out"; mkdir -p "$out"
  echo "== compiling tests of $name"
  # shellcheck disable=SC2086
  $JAVAC $flags -cp "$cp:$CP_TEST" -d "$out" $(find "$src" -name '*.java')
  [ -d "$res" ] && cp -r "$res/." "$out/"
  local tests
  tests=$(cd "$src" && find . -name '*Test.java' | sed 's#^\./##; s#\.java$##; s#/#.#g' | sort | tr '\n' ' ')
  echo "== running tests of $name"
  # shellcheck disable=SC2086
  java -cp "$out:$cp:$CP_TEST" org.junit.runner.JUnitCore $tests
}

CORE_CP="$CP_BASE"
build_module webbeans-core "$CORE_CP" "-proc:none"
test_module  webbeans-core "$OUT/webbeans-core.jar:$CORE_CP" "-proc:none"

PROC_CP="$OUT/webbeans-core.jar:$CP_BASE"
build_module webbeans-processor "$PROC_CP" "-proc:none"
test_module  webbeans-processor "$OUT/webbeans-processor.jar:$PROC_CP" "-proc:none"

# the WebBeans processor plus NetBeans' @ServiceProvider processor
PROCS="-processorpath $OUT/webbeans-processor.jar:$OUT/webbeans-core.jar:$CP_BASE"

AUTH_CP="$OUT/webbeans-core.jar:$CP_BASE"
build_module webbeans-auth "$AUTH_CP" "-proc:none"
test_module  webbeans-auth "$OUT/webbeans-auth.jar:$AUTH_CP" "-proc:none"

UI_CP="$OUT/webbeans-auth.jar:$OUT/webbeans-core.jar:$CP_BASE"
build_module webbeans-ui "$UI_CP" "$PROCS -Awebbeans.module=org.webbeans.ui"
test_module  webbeans-ui "$OUT/webbeans-ui.jar:$UI_CP" "-proc:none"

build_module webbeans-windows-processor "$OUT/webbeans-processor.jar:$CP_BASE" "-proc:none"
WPROCS="$PROCS:$OUT/webbeans-windows-processor.jar"

WINDOWS_CP="$OUT/webbeans-ui.jar:$UI_CP"
build_module webbeans-windows "$WINDOWS_CP" "$WPROCS -Awebbeans.module=org.webbeans.windows"
test_module  webbeans-windows "$OUT/webbeans-windows.jar:$OUT/webbeans-ui.jar:$UI_CP" "-proc:none"

build_module webbeans-actions-processor "$OUT/webbeans-processor.jar:$CP_BASE" "-proc:none"
APROCS="$WPROCS:$OUT/webbeans-actions-processor.jar"

ACTIONS_CP="$OUT/webbeans-windows.jar:$OUT/webbeans-ui.jar:$UI_CP"
build_module webbeans-actions "$ACTIONS_CP" "$APROCS -Awebbeans.module=org.webbeans.actions"
test_module  webbeans-actions "$OUT/webbeans-actions.jar:$ACTIONS_CP" "-proc:none"

NODES_CP="$OUT/webbeans-actions.jar:$OUT/webbeans-windows.jar:$OUT/webbeans-ui.jar:$UI_CP"
build_module webbeans-nodes "$NODES_CP" "$APROCS -Awebbeans.module=org.webbeans.nodes"
test_module  webbeans-nodes "$OUT/webbeans-nodes.jar:$NODES_CP" "-proc:none"

CHARTS_CP="$OUT/webbeans-actions.jar:$OUT/webbeans-windows.jar:$OUT/webbeans-ui.jar:$UI_CP"
build_module webbeans-charts "$CHARTS_CP" "$APROCS -Awebbeans.module=org.webbeans.charts"
test_module  webbeans-charts "$OUT/webbeans-charts.jar:$CHARTS_CP" "-proc:none"

SCENE3D_CP="$OUT/webbeans-nodes.jar:$OUT/webbeans-actions.jar:$OUT/webbeans-windows.jar:$OUT/webbeans-ui.jar:$UI_CP"
build_module webbeans-scene3d "$SCENE3D_CP" "$APROCS -Awebbeans.module=org.webbeans.scene3d"
test_module  webbeans-scene3d "$OUT/webbeans-scene3d.jar:$SCENE3D_CP" "-proc:none"

TEST_CP="$OUT/webbeans-nodes.jar:$OUT/webbeans-actions.jar:$OUT/webbeans-windows.jar:$OUT/webbeans-ui.jar:$UI_CP"
build_module webbeans-test "$TEST_CP" "-proc:none"

HELP_CP="$OUT/webbeans-nodes.jar:$OUT/webbeans-actions.jar:$OUT/webbeans-windows.jar:$OUT/webbeans-ui.jar:$UI_CP"
build_module webbeans-help "$HELP_CP" "$APROCS -Awebbeans.module=org.webbeans.help"
test_module webbeans-help "$OUT/webbeans-help.jar:$OUT/webbeans-test.jar:$HELP_CP" "$APROCS -Awebbeans.module=org.webbeans.help.test"

SERVER_CP="$OUT/webbeans-ui.jar:$UI_CP:$JETTY_CP"
build_module webbeans-server "$SERVER_CP" "-proc:none"
test_module  webbeans-server "$OUT/webbeans-server.jar:$SERVER_CP:$JETTY_CLIENT_CP" "-proc:none"

LAUNCHER_CP="$OUT/webbeans-server.jar:$SERVER_CP"
build_module webbeans-launcher "$LAUNCHER_CP" "-proc:none"

DEMO_CP="$OUT/webbeans-help.jar:$OUT/webbeans-scene3d.jar:$OUT/webbeans-charts.jar:$OUT/webbeans-nodes.jar:$OUT/webbeans-actions.jar:$OUT/webbeans-windows.jar:$OUT/webbeans-ui.jar:$UI_CP"
build_module webbeans-demo "$DEMO_CP" "$APROCS -Awebbeans.module=org.webbeans.demo"

# the tutorial module of docs/user-guide.md (built and tested like any application module)
build_module webbeans-tutorial "$DEMO_CP" "$APROCS -Awebbeans.module=org.webbeans.tutorial"
test_module webbeans-tutorial "$OUT/webbeans-tutorial.jar:$OUT/webbeans-test.jar:$DEMO_CP" "-proc:none"

# ---- the Workbench sample application -------------------------------------------------------------------------
WB_CP="$OUT/webbeans-help.jar:$OUT/webbeans-scene3d.jar:$OUT/webbeans-charts.jar:$OUT/webbeans-nodes.jar:$OUT/webbeans-actions.jar:$OUT/webbeans-windows.jar:$OUT/webbeans-ui.jar:$UI_CP"
build_module workbench/workbench-core "$WB_CP" "$APROCS -Awebbeans.module=org.webbeans.workbench.core"
WBCORE="$OUT/workbench/workbench-core.jar"
for m in files dashboard viewer3d admin; do
  [ -d "workbench/workbench-$m/src/main/java" ] || continue
  build_module workbench/workbench-$m "$WBCORE:$WB_CP" "$APROCS -Awebbeans.module=org.webbeans.workbench.$m"
done
WBTEST_CP="$OUT/webbeans-test.jar:$WBCORE:$WB_CP"
for m in core files dashboard viewer3d admin; do
  [ -d "workbench/workbench-$m/src/test/java" ] || continue
  test_module workbench/workbench-$m "$OUT/workbench/workbench-$m.jar:$WBTEST_CP" "-proc:none"
done

# the Workbench application jar: platform + branding resources + embedded modules (see tools/fatjar.py)
WBMODS=""
for m in core files dashboard viewer3d admin; do [ -f "$OUT/workbench/workbench-$m.jar" ] && WBMODS="$WBMODS $OUT/workbench/workbench-$m.jar"; done
mkdir -p "$OUT/workbench-dist"
# shellcheck disable=SC2086
python3 tools/fatjar.py "$OUT/workbench-dist/workbench.jar" \
  --lib "$LIB/org-openide-util-lookup.jar" "$LIB/org-openide-util.jar" $(for j in $JETTY_JARS; do echo "$LIB/$j"; done) $(for m in core auth ui windows actions nodes help charts scene3d server launcher; do echo "$OUT/webbeans-$m.jar"; done) \
  --modules $WBMODS --resources workbench/workbench-app/src/main/resources
cp workbench/workbench-app/workbench.sh "$OUT/workbench-dist/"

# runnable layout: platform jars in dist/lib, modules in dist/modules
rm -rf "$OUT/dist"; mkdir -p "$OUT/dist/lib" "$OUT/dist/modules"
cp "$LIB/org-openide-util-lookup.jar" "$LIB/org-openide-util.jar" "$OUT/dist/lib/"
for j in $JETTY_JARS; do cp "$LIB/$j" "$OUT/dist/lib/"; done
for m in core auth ui windows actions nodes help charts scene3d test server launcher; do cp "$OUT/webbeans-$m.jar" "$OUT/dist/lib/"; done
cp "$OUT/webbeans-demo.jar" "$OUT/webbeans-tutorial.jar" "$OUT/dist/modules/"

echo "== OK: $(ls "$OUT"/*.jar | xargs -n1 basename | tr '\n' ' ')"
echo "run:  java -cp '$OUT/dist/lib/*' org.webbeans.launcher.Main --modules $OUT/dist/modules --datadir data --port 8080"
