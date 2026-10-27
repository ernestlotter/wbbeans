#!/bin/sh
# Renders the Markdown guides to PDF (needs pandoc, node + playwright with Chromium).
#   ./build-pdf.sh                 -> every guide listed below
#   ./build-pdf.sh authentication  -> one of user-guide | developer-guide | component-guide | authentication
set -e
cd "$(dirname "$0")"
render() {   # render <source .md, relative to the repo root> <pdf name> <title>
  OUT=$(mktemp -d)
  cp -r ../images "$OUT/" && cp guide.css "$OUT/"
  tail -n +2 "../../$1" > "$OUT/body.md"            # the H1 becomes the title block
  pandoc "$OUT/body.md" -f gfm+smart -t html5 -s --toc --toc-depth=3 \
    --metadata title="$3" --metadata date="$(date +%Y-%m-%d)" \
    --highlight-style=pygments -c guide.css -o "$OUT/guide.html"
  node print.js "$OUT/guide.html" "$2" "$3" "webbeans / $1"
  rm -rf "$OUT"
  echo "wrote docs/pdf/$2"
}
want() { [ "${1:-all}" = all ] || [ "$1" = "$2" ]; }
want "$1" user-guide      && render docs/user-guide.md         WebBeans-User-Guide.pdf           "WebBeans User's Guide for Developers"
want "$1" developer-guide && render docs/developer-guide.md    WebBeans-Developer-Guide.pdf      "WebBeans Developer Guide"
want "$1" component-guide && render docs/component-guide.md   WebBeans-Component-Guide.pdf      "WebBeans Component Guide"
want "$1" authentication  && render design/authentication.md  WebBeans-Authentication-Guide.pdf "WebBeans Authentication Guide"
exit 0
