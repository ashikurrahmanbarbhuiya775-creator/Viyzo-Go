#!/bin/sh
set -eu

DATA_DIR="${DATA_DIR:-/data}"
BACKUP_DIR="${BACKUP_DIR:-/backups}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
ARCHIVE="$BACKUP_DIR/viyzo-media-$STAMP.tar.gz"

if [ ! -d "$DATA_DIR" ]; then
  echo "Data directory does not exist: $DATA_DIR" >&2
  exit 1
fi
mkdir -p "$BACKUP_DIR"
umask 077
tar -czf "$ARCHIVE" -C "$DATA_DIR" .
(
  cd "$BACKUP_DIR"
  sha256sum "$(basename "$ARCHIVE")" > "$(basename "$ARCHIVE").sha256"
)
chmod 600 "$ARCHIVE" "$ARCHIVE.sha256"
echo "Backup created: $ARCHIVE"
echo "Checksum: $ARCHIVE.sha256"
