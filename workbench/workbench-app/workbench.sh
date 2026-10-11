#!/usr/bin/env bash
# Starts the Workbench from the application jar next to this script.
cd "$(dirname "$0")"
exec java -jar workbench.jar --branding workbench --title Workbench --datadir "${WORKBENCH_DATA:-data}" --port "${PORT:-8080}" "$@"
