#!/usr/bin/env bash
set -euo pipefail

SRC_DIR="/home/player/docker/shine-UI"
REMOTE_HOST="root@194.87.0.247"
REMOTE_DIR="/home/user/docker/caddyFile/sites/shine-UI"
BUILD_VERSION="$(date -u +%Y%m%d%H%M%S)"
export BUILD_VERSION

if [[ ! -d "$SRC_DIR" ]]; then
  echo "ERROR: source directory not found: $SRC_DIR" >&2
  exit 1
fi

echo "==> Applying build version: $BUILD_VERSION"
find "$SRC_DIR" -type f \( -name "*.js" -o -name "index.html" \) -print0 | xargs -0 perl -0pi -e 's/(\.js\?v=)([^"'"'"'\''\s>]*)/$1$ENV{BUILD_VERSION}/g; s/(\.css\?v=)([^"'"'"'\''\s>]*)/$1$ENV{BUILD_VERSION}/g'

echo "==> Checking SSH connectivity to $REMOTE_HOST"
ssh -o BatchMode=yes -o ConnectTimeout=10 "$REMOTE_HOST" "echo SSH OK" >/dev/null

echo "==> Preparing remote directory: $REMOTE_DIR"
ssh "$REMOTE_HOST" "mkdir -p '$REMOTE_DIR'"

echo "==> Syncing files from $SRC_DIR to $REMOTE_DIR"
rsync -avz --delete "$SRC_DIR"/ "$REMOTE_HOST":"$REMOTE_DIR"/

echo "Всё хорошо"
