#!/usr/bin/env bash
# Offline build for environments without Maven Central access.
# Compiles every module with javac --release 8 against the jars in $LIB and runs the JUnit tests.
# The canonical build is `mvn verify`; this script mirrors it module by module.
#
# Usage: build.sh [--jdk <path>]
#   --jdk <path>   the JDK to build with (its bin/ must hold javac, jar and java); the same as
#                  setting JAVA_HOME. Without either, the tools are taken from the PATH.
# Environment:  JAVA_HOME (JDK), LIB (third-party jars), OUT (build output), JETTY_VERSION.
set -euo pipefail
cd "$(dirname "$0")"

while [ $# -gt 0 ]; do
  case "$1" in
    --jdk)   [ $# -ge 2 ] || { echo "build.sh: --jdk needs a path" >&2; exit 2; }; JAVA_HOME="$2"; shift 2 ;;
    --jdk=*) JAVA_HOME="${1#--jdk=}"; shift ;;
    -h|--help) sed -n '2,/^set -euo/p' "$0" | grep '^#' | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "build.sh: unknown option $1 (try --help)" >&2; exit 2 ;;
  esac
done

# The JDK: $JAVA_HOME/bin when JAVA_HOME (or --jdk) is given, else whatever is on the PATH.
if [ -n "${JAVA_HOME:-}" ]; then
  JDK_BIN="${JAVA_HOME%/}/bin"
  for t in javac jar java; do
    [ -x "$JDK_BIN/$t" ] || { echo "build.sh: $JDK_BIN/$t not found or not executable (JAVA_HOME=$JAVA_HOME)" >&2; exit 2; }
  done
  JAVAC_BIN="$JDK_BIN/javac"; JAR_BIN="$JDK_BIN/jar"; JAVA_BIN="$JDK_BIN/java"
  export JAVA_HOME
else
  for t in javac jar java; do
    command -v "$t" >/dev/null 2>&1 || { echo "build.sh: $t not on the PATH; set JAVA_HOME or pass --jdk <path>" >&2; exit 2; }
  done
  JAVAC_BIN=javac; JAR_BIN=jar; JAVA_BIN=java
fi
JAVAC_VERSION=$("$JAVAC_BIN" -version 2>&1 | grep -v '^Picked up' | head -1)   # "javac 1.8.0_432" or "javac 21.0.1"
JAVAC_MAJOR=$(echo "$JAVAC_VERSION" | sed -E 's/^javac //; s/^1\.//; s/[^0-9].*$//')
case "$JAVAC_MAJOR" in ''|*[!0-9]*) echo "build.sh: cannot parse the javac version '$JAVAC_VERSION'" >&2; exit 2 ;; esac
[ "$JAVAC_MAJOR" -ge 8 ] || { echo "build.sh: JDK 8 or newer required, found $JAVAC_VERSION" >&2; exit 2; }
echo "== JDK: $JAVAC_VERSION ($(command -v "$JAVAC_BIN"))"

# Java 8 class files and API from any JDK: javac 8 only knows -source/-target (it is the Java 8 API itself);
# javac 9+ has --release, which also checks the API against the Java 8 signatures.
if [ "$JAVAC_MAJOR" -ge 9 ]; then TARGET_FLAGS="--release 8"; else TARGET_FLAGS="-source 8 -target 8"; fi
# Every warning is an error, except the ones that only reflect the build setup. Older javacs reject lint keys
# they do not know, so each key is added only where it exists.
LINT="all,-options,-processing"
[ "$JAVAC_MAJOR" -ge 21 ] && LINT="$LINT,-this-escape"

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
JAVAC="$JAVAC_BIN $TARGET_FLAGS -Xlint:$LINT -Werror -encoding UTF-8"

# The third-party jars: fetched by tools/fetch-third-party.sh on the first build (or whenever one is missing).
missing_jars() { for p in $(echo "$CP_BASE:$CP_TEST:$JETTY_CP:$JETTY_CLIENT_CP" | tr ':' ' '); do [ -f "$p" ] || basename "$p"; done; }
MISSING=$(missing_jars)
if [ -n "$MISSING" ]; then
  echo "== fetching third-party jars into $LIB ($(echo $MISSING | wc -w) missing)"
  LIB="$LIB" JETTY_VERSION="$JETTY_VERSION" bash tools/fetch-third-party.sh || true
  MISSING=$(missing_jars)
  if [ -n "$MISSING" ]; then
    echo "build.sh: these jars are missing from $LIB and could not be downloaded:" >&2
    echo "$MISSING" | sed 's/^/  /' >&2
    echo "Run tools/fetch-third-party.sh with access to Maven Central, or copy the jars there (LIB=<dir> overrides the directory)." >&2
    exit 2
  fi
fi

build_module() {   # name  classpath  extra-javac-flags
  local name=$1 cp=$2 flags=${3:-}
  local src="$name/src/main/java" res="$name/src/main/resources" out="$OUT/$name/classes"
  local gen="$OUT/$name/generated-sources"
  rm -rf "$out" "$gen"; mkdir -p "$out" "$gen"
  echo "== compiling $name"
  # resources first, so the processor sees a hand-written Bundle.properties in the class output as well
  [ -d "$res" ] && cp -r "$res/." "$out/"
  (cd "$src" && find . -type f ! -name '*.java' -exec cp --parents {} "$out/" \;)
  # shellcheck disable=SC2086
  $JAVAC $flags -sourcepath "$src" -cp "$cp" -d "$out" -s "$gen" $(find "$src" -name '*.java')
  if [ -f "$res/META-INF/MANIFEST.MF" ]; then
    (cd "$out" && "$JAR_BIN" cfm "$OUT/$name.jar" META-INF/MANIFEST.MF .)
  else
    (cd "$out" && "$JAR_BIN" cf "$OUT/$name.jar" .)
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
  "$JAVA_BIN" -cp "$out:$cp:$CP_TEST" org.junit.runner.JUnitCore $tests
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

GRAPH_CP="$OUT/webbeans-actions.jar:$OUT/webbeans-windows.jar:$OUT/webbeans-ui.jar:$UI_CP"
build_module webbeans-graph "$GRAPH_CP" "$APROCS -Awebbeans.module=org.webbeans.graph"
test_module  webbeans-graph "$OUT/webbeans-graph.jar:$GRAPH_CP" "-proc:none"

MAP_CP="$OUT/webbeans-actions.jar:$OUT/webbeans-windows.jar:$OUT/webbeans-ui.jar:$UI_CP"
build_module webbeans-map "$MAP_CP" "$APROCS -Awebbeans.module=org.webbeans.map"
test_module  webbeans-map "$OUT/webbeans-map.jar:$MAP_CP" "-proc:none"

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

DEMO_CP="$OUT/webbeans-help.jar:$OUT/webbeans-scene3d.jar:$OUT/webbeans-map.jar:$OUT/webbeans-graph.jar:$OUT/webbeans-charts.jar:$OUT/webbeans-nodes.jar:$OUT/webbeans-actions.jar:$OUT/webbeans-windows.jar:$OUT/webbeans-ui.jar:$UI_CP"
build_module webbeans-demo "$DEMO_CP" "$APROCS -Awebbeans.module=org.webbeans.demo"

# the tutorial module of docs/user-guide.md (built and tested like any application module)
build_module webbeans-tutorial "$DEMO_CP" "$APROCS -Awebbeans.module=org.webbeans.tutorial"
test_module webbeans-tutorial "$OUT/webbeans-tutorial.jar:$OUT/webbeans-test.jar:$DEMO_CP" "-proc:none"

# ---- the Workbench sample application -------------------------------------------------------------------------
WB_CP="$OUT/webbeans-help.jar:$OUT/webbeans-scene3d.jar:$OUT/webbeans-map.jar:$OUT/webbeans-graph.jar:$OUT/webbeans-charts.jar:$OUT/webbeans-nodes.jar:$OUT/webbeans-actions.jar:$OUT/webbeans-windows.jar:$OUT/webbeans-ui.jar:$UI_CP"
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

# the Workbench application jar: platform + the Workbench jars + branding resources merged (see tools/fatjar.py)
WBJARS=""
for m in core files dashboard viewer3d admin; do [ -f "$OUT/workbench/workbench-$m.jar" ] && WBJARS="$WBJARS $OUT/workbench/workbench-$m.jar"; done
mkdir -p "$OUT/workbench-dist"
# shellcheck disable=SC2086
python3 tools/fatjar.py "$OUT/workbench-dist/workbench.jar" \
  --lib "$LIB/org-openide-util-lookup.jar" "$LIB/org-openide-util.jar" $(for j in $JETTY_JARS; do echo "$LIB/$j"; done) $(for m in core auth ui windows actions nodes help charts graph map scene3d server launcher; do echo "$OUT/webbeans-$m.jar"; done) $WBJARS \
  --resources workbench/workbench-app/src/main/resources
cp workbench/workbench-app/workbench.sh "$OUT/workbench-dist/"

# runnable layout: everything (platform, demo and tutorial) in dist/lib — the class path is the application
rm -rf "$OUT/dist"; mkdir -p "$OUT/dist/lib"
cp "$LIB/org-openide-util-lookup.jar" "$LIB/org-openide-util.jar" "$OUT/dist/lib/"
for j in $JETTY_JARS; do cp "$LIB/$j" "$OUT/dist/lib/"; done
for m in core auth ui windows actions nodes help charts graph map scene3d test server launcher demo tutorial; do cp "$OUT/webbeans-$m.jar" "$OUT/dist/lib/"; done

echo "== OK: $(ls "$OUT"/*.jar | xargs -n1 basename | tr '\n' ' ')"
echo "run:  $JAVA_BIN -cp '$OUT/dist/lib/*' org.webbeans.launcher.Main --datadir data --port 8080"
