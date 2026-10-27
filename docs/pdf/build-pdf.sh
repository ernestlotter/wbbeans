#!/bin/sh
# Renders the Markdown guides in docs/ to PDF (needs pandoc, node + playwright with Chromium).
#   ./build-pdf.sh            -> all three guides
#   ./build-pdf.sh user-guide -> one of user-guide | developer-guide | component-guide
set -e
cd "$(dirname "$0")"
render() {   # render <basename> <pdf name> <title>
  OUT=$(mktemp -d)
  cp -r ../images "$OUT/" && cp guide.css "$OUT/"
  tail -n +2 "../$1.md" > "$OUT/body.md"            # the H1 becomes the title block
  pandoc "$OUT/body.md" -f gfm+smart -t html5 -s --toc --toc-depth=3 \
    --metadata title="$3" --metadata date="$(date +%Y-%m-%d)" \
    --highlight-style=pygments -c guide.css -o "$OUT/guide.html"
  node print.js "$OUT/guide.html" "$2" "$3" "webbeans / docs/$1.md"
  rm -rf "$OUT"
  echo "wrote docs/pdf/$2"
}
case "${1:-all}" in
  user-guide|all)      render user-guide      WebBeans-User-Guide.pdf      "WebBeans User's Guide for Developers" ;;
esac
case "${1:-all}" in
  developer-guide|all) render developer-guide WebBeans-Developer-Guide.pdf "WebBeans Developer Guide" ;;
esac
case "${1:-all}" in
  component-guide|all) render component-guide WebBeans-Component-Guide.pdf "WebBeans Component Guide" ;;
esac
