#!/bin/sh
# Renders docs/user-guide.md to docs/pdf/WebBeans-User-Guide.pdf (needs pandoc, node + playwright with Chromium).
set -e
cd "$(dirname "$0")"
OUT=$(mktemp -d)
cp -r ../images "$OUT/" && cp guide.css print.js "$OUT/"
tail -n +2 ../user-guide.md > "$OUT/guide-body.md"       # the H1 becomes the title block
pandoc "$OUT/guide-body.md" -f gfm+smart -t html5 -s --toc --toc-depth=3 \
  --metadata title="WebBeans User's Guide for Developers" --metadata date="$(date +%Y-%m-%d)" \
  --highlight-style=pygments -c guide.css -o "$OUT/guide.html"
node "$OUT/print.js" && mv "$OUT/WebBeans-User-Guide.pdf" . && rm -rf "$OUT"
echo "wrote docs/pdf/WebBeans-User-Guide.pdf"
