#!/usr/bin/env bash
# Downloads the jars the offline build (build.sh) compiles against into ../third_party/lib (or $LIB).
# The Maven build resolves the same artifacts itself; this is only for environments without Maven.
set -euo pipefail
LIB="${LIB:-$(cd "$(dirname "$0")/.." && pwd)/../third_party/lib}"
REPO="${MAVEN_CENTRAL:-https://repo1.maven.org/maven2}"
JETTY_VERSION="${JETTY_VERSION:-9.4.58.v20250814}"
NB="${NETBEANS_VERSION:-RELEASE126}"
mkdir -p "$LIB"

fetch() {  # group/path artifact version [target-name]
  local g=$1 a=$2 v=$3 name=${4:-$2-$3.jar}
  if [ -f "$LIB/$name" ]; then echo "have $name"; return; fi
  echo "get  $name"
  curl -fsSL -o "$LIB/$name" "$REPO/$g/$a/$v/$a-$v.jar"
}

fetch org/netbeans/api org-openide-util-lookup "$NB" org-openide-util-lookup.jar
fetch org/netbeans/api org-openide-util "$NB" org-openide-util.jar
fetch junit junit 4.13.2
fetch org/hamcrest hamcrest-core 1.3
fetch javax/servlet javax.servlet-api 3.1.0
for a in jetty-util jetty-jmx jetty-io jetty-http jetty-server jetty-security jetty-util-ajax jetty-servlet jetty-xml jetty-client; do
  fetch org/eclipse/jetty "$a" "$JETTY_VERSION"
done
for a in websocket-api websocket-common websocket-servlet websocket-server websocket-client; do
  fetch org/eclipse/jetty/websocket "$a" "$JETTY_VERSION"
done
echo "jars in $LIB:"; ls -1 "$LIB"
