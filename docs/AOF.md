# Append-only file (AOF)

## Enabling

| Setting | Default | Notes |
|---------|---------|-------|
| `myredis.aofEnabled` / `MYREDIS_AOF_ENABLED` | `false` | When `true`, mutating commands are logged to `appendonly.aof` under the persistence directory. |
| `myredis.aofFsyncPolicy` / `MYREDIS_AOF_FSYNC_POLICY` | `EVERY_SEC` | One of `ALWAYS`, `EVERY_SEC`, `NO`. |

## On-disk format

- File: `appendonly.aof`
- **v2 header**: `MYRAOF` magic, format version, `baselineSnapshotCrc32`, `baselineRecordCount` (records that duplicate the on-disk snapshot after `SAVE`)
- **v1 header**: magic + version only (legacy)
- Each command is a **length-prefixed** RESP2 bulk-string array so a partial last record is ignored on recovery.

## Recovery

See [RECOVERY.md](RECOVERY.md) for the full startup sequence. Summary:

1. Load `dump.myredis` when present.
2. Verify AOF baseline CRC against the snapshot file when using v2.
3. Replay only AOF records **after** the snapshot baseline (skip duplicate compacted commands).
4. Ignore an incomplete trailing frame at EOF.

`SAVE` / `BGSAVE` rewrite the AOF with a fresh baseline tied to the snapshot CRC.

## Compaction

- `BGREWRITEAOF` rebuilds the log from the current keyspace (minimal command set).
- Automatic rewrite also runs after a successful snapshot save when AOF is enabled.
