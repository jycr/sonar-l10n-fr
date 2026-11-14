#!/usr/bin/env sh
# Capture d'écran de preuve, dans l'image officielle Playwright : rien à installer sur la machine.
#
# Usage : capture.sh <url> <sortie.png> [options de capture.mjs]
#   ex. : capture.sh http://localhost:9020/admin/background_tasks /tmp/preuve.png --sonar --texte "Âge de la plus ancienne"
#
# --network host : localhost désigne la machine, donc la stack de recette (port 9020) est joignable.
# SQ_USER / SQ_PASSWORD sont transmis au conteneur pour --sonar.
set -eu
if [ $# -lt 2 ]; then
  echo "Usage: $0 <url> <sortie.png> [--sonar] [--texte ...] [--selecteur css] [--pleine-page]" >&2
  exit 2
fi
PLAYWRIGHT_VERSION=1.64.1
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
URL="$1"
OUT="$2"
shift 2
OUT_DIR=$(cd "$(dirname "$OUT")" && pwd)
OUT_NAME=$(basename "$OUT")

# Le paquet npm playwright est installé une fois dans un volume, à la même version que l'image.
exec docker run --rm --network host --init \
  -e SQ_USER -e SQ_PASSWORD \
  -v sonar-l10n-fr-playwright:/opt/capture/node_modules \
  -v "$SCRIPT_DIR/capture.mjs":/opt/capture/capture.mjs:ro \
  -v "$OUT_DIR":/out \
  -w /opt/capture \
  "mcr.microsoft.com/playwright:v$PLAYWRIGHT_VERSION-noble" \
  sh -c '[ -d node_modules/playwright ] || npm install --no-save --no-audit --no-fund --silent playwright@'"$PLAYWRIGHT_VERSION"' >/dev/null
         exec node capture.mjs "$@"' capture "$URL" "/out/$OUT_NAME" "$@"
