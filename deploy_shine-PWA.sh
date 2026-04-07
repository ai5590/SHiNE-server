#!/usr/bin/env bash
set -euo pipefail

SRC_DIR="shine-UI"
REMOTE_HOST="root@194.87.0.247"
REMOTE_DIR="/home/user/docker/caddyFile/sites/shine-UI"
BUILD_VERSION="$(date -u +%Y%m%d%H%M%S)"
export BUILD_VERSION
TMP_DIR="$(mktemp -d)"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

if [[ ! -d "$SRC_DIR" ]]; then
  echo "ERROR: source directory not found: $SRC_DIR" >&2
  exit 1
fi

echo "==> Preparing staged UI copy with build version: $BUILD_VERSION"
rsync -a "$SRC_DIR"/ "$TMP_DIR"/

INDEX_FILE="$TMP_DIR/index.html"
if [[ ! -f "$INDEX_FILE" ]]; then
  echo "ERROR: index.html not found in staged UI: $INDEX_FILE" >&2
  exit 1
fi

perl -0pi -e 's/window\.__SHINE_BUILD_HASH__\s*=\s*'\''[^'\'']*'\'';/window.__SHINE_BUILD_HASH__ = '\''$ENV{BUILD_VERSION}'\'';/' "$INDEX_FILE"

echo "==> Checking SSH connectivity to $REMOTE_HOST"
ssh -o BatchMode=yes -o ConnectTimeout=10 "$REMOTE_HOST" "echo SSH OK" >/dev/null

echo "==> Preparing remote directory: $REMOTE_DIR"
ssh "$REMOTE_HOST" "mkdir -p '$REMOTE_DIR'"

echo "==> Syncing staged files to $REMOTE_DIR"
rsync -avz --delete "$TMP_DIR"/ "$REMOTE_HOST":"$REMOTE_DIR"/

echo "Всё хорошо"
