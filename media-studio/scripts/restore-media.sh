#!/bin/sh
set -eu

ARCHIVE="${1:-}"
DATA_DIR="${DATA_DIR:-/data}"
if [ -z "$ARCHIVE" ] || [ ! -f "$ARCHIVE" ]; then
  echo "Usage: restore-media.sh /backups/viyzo-media-TIMESTAMP.tar.gz" >&2
  exit 2
fi
CHECKSUM="$ARCHIVE.sha256"
if [ ! -f "$CHECKSUM" ]; then
  echo "Missing checksum file: $CHECKSUM" >&2
  exit 1
fi
(
  cd "$(dirname "$ARCHIVE")"
  sha256sum -c "$(basename "$CHECKSUM")"
)

# Reject absolute paths and parent traversal before extraction.
if tar -tzf "$ARCHIVE" | grep -E '(^/|(^|/)\.\.(/|$))' >/dev/null; then
  echo "Unsafe archive paths detected; refusing restore." >&2
  exit 1
fi

if [ -d "$DATA_DIR" ] && [ -n "$(find "$DATA_DIR" -mindepth 1 -maxdepth 1 -print -quit)" ] && [ "${FORCE_RESTORE:-0}" != "1" ]; then
  echo "Data directory is not empty. Stop the service, back it up, then set FORCE_RESTORE=1 to replace it." >&2
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
tar -xzf "$ARCHIVE" -C "$TMP"
if [ ! -f "$TMP/jobs.json" ]; then
  echo "Archive does not contain jobs.json; refusing restore." >&2
  exit 1
fi
mkdir -p "$DATA_DIR"
cp -a "$TMP/." "$DATA_DIR/"
chmod 600 "$DATA_DIR/jobs.json"
echo "Restore completed into $DATA_DIR"
