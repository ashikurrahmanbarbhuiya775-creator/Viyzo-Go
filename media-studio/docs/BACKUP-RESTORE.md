# Media Studio backup and restore

The Media Studio data volume contains the job database and private media files. Back it up before upgrades, migrations, or provider changes.

## Create a backup

On the deployment host, from `media-studio/`:

```sh
mkdir -p ./backups
docker compose run --rm --no-deps \
  -e DATA_DIR=/data -e BACKUP_DIR=/backups \
  -v media_data:/data -v "$PWD/backups:/backups" \
  --entrypoint sh media-studio scripts/backup-media.sh
```

The script creates a timestamped gzip archive and a SHA-256 checksum. Keep both files together and store an encrypted off-server copy. The archive contains private videos and must be treated as sensitive data.

## Restore

1. Stop the service: `docker compose stop media-studio`.
2. Put the archive and its `.sha256` file in a protected backup directory.
3. Verify the archive and restore into the persistent volume:

```sh
docker compose run --rm --no-deps \
  -e DATA_DIR=/data -e FORCE_RESTORE=1 \
  -v media_data:/data -v "$PWD/backups:/backups:ro" \
  --entrypoint sh media-studio \
  scripts/restore-media.sh /backups/viyzo-media-TIMESTAMP.tar.gz
```

4. Start the service: `docker compose start media-studio`, then check `/health` and sign in.

Restore verifies the checksum, rejects unsafe archive paths, and requires a valid `jobs.json`. `FORCE_RESTORE=1` is deliberately required before replacing a non-empty data directory. Stop all writers before restoring.

## Important limitations

- This is an operator-run backup, not an automatic scheduled backup.
- Keep backups off the application host; a volume on the same machine is not disaster recovery.
- Test restores periodically in a separate volume before relying on them.
- Never commit archives, passwords, API keys, or owner session data to Git.
