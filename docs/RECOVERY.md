# Startup recovery (snapshot + AOF)

My-Redis combines an RDB-style snapshot (`dump.myredis`) with an optional append-only log (`appendonly.aof`). Recovery runs in `ServerApplication.run()` before the server accepts clients.

## Order of operations

1. **Load snapshot** — If `dump.myredis` exists, decode and validate CRC, magic, and version, then populate the keyspace and expiration metadata. A missing file starts from an empty database.
2. **Validate AOF baseline** — When AOF v2 is present, compare the header’s `baselineSnapshotCrc32` to the on-disk snapshot CRC. A mismatch is a hard error (prevents replaying a log against the wrong image).
3. **Replay AOF** — Apply complete length-prefixed records. Records that belong to the snapshot baseline (same CRC and index ≤ `baselineRecordCount`) are **skipped** so compacted baseline commands are not applied twice.
4. **Ignore partial tail** — Any incomplete length-prefixed frame at EOF is ignored.
5. **Integrity checks** — Key count must be non-negative and expiration metadata must not track more keys than exist.

During replay, the dispatcher does **not** append to AOF or write snapshots (no persistence side effects).

## Preventing duplicate replay

| Event | Effect |
|-------|--------|
| `SAVE` / `BGSAVE` (with AOF enabled) | After the snapshot is written, AOF is rewritten and the v2 header records the snapshot file CRC and the number of baseline commands. |
| New writes | Append after the baseline; recovery skips the baseline and replays only newer records. |
| AOF v1 | No baseline fields; all records are replayed when a snapshot also exists (legacy behaviour). Run `SAVE` once to upgrade to v2. |

## Data integrity

- Snapshot load fails on checksum / magic / truncation errors (see [SNAPSHOT.md](SNAPSHOT.md)).
- AOF load fails on magic / version / baseline CRC mismatch with an existing snapshot.
- `KeyspaceIntegrityDigest` provides a deterministic CRC for tests and tooling after recovery.
