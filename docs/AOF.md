# Append-only file (AOF)

## Enabling

| Setting | Default | Notes |
|---------|---------|-------|
| `myredis.aofEnabled` / `MYREDIS_AOF_ENABLED` | `false` | When `true`, mutating commands are logged to `appendonly.aof` under the persistence directory. |
| `myredis.aofFsyncPolicy` / `MYREDIS_AOF_FSYNC_POLICY` | `EVERY_SEC` | One of `ALWAYS`, `EVERY_SEC`, `NO`. |

## On-disk format

- File: `appendonly.aof`
- Fixed header (`MYRAOF` magic + version)
- Each command is stored as a **length-prefixed** RESP2 bulk-string array so a partial last record is ignored on recovery.

## Recovery

On startup (when AOF is enabled):

1. Load RDB snapshot (`dump.myredis`) if present.
2. Replay every complete AOF record.
3. Ignore any incomplete trailing frame at EOF.

`SAVE` / `BGSAVE` rewrite the AOF to match the snapshot so snapshot + incremental AOF stay consistent.

## Compaction

- `BGREWRITEAOF` rebuilds the log from the current keyspace (minimal command set).
- Automatic rewrite also runs after a successful snapshot save when AOF is enabled.
