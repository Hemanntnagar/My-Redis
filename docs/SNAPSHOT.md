# My-Redis snapshot persistence

My-Redis stores a point-in-time image of the in-memory database in a single file under the configured persistence directory (default: `./data`).

## File layout

| Field | Size | Description |
|-------|------|-------------|
| Body | variable | Serialized payload (see below) |
| CRC32 | 4 bytes | IEEE CRC32 of the body (big-endian `uint32`) |

The on-disk name is `dump.myredis`. Writes go to `dump.myredis.tmp`, are `fsync`’d, then renamed over `dump.myredis` so readers never see a partial file.

### Body layout (version 1)

| Field | Size | Description |
|-------|------|-------------|
| Magic | 8 bytes | ASCII `MYRDSNAP` |
| Version | 2 bytes | Unsigned big-endian, currently `1` |
| Entry count | 8 bytes | Unsigned big-endian |
| Entries | repeated | See entry format |
| CRC32 | (footer) | Appended after body when writing the file |

### Entry format

Each entry is stored in unsigned lexicographic key order.

| Field | Size | Description |
|-------|------|-------------|
| Key length | 4 bytes | `int32` |
| Key | *length* | Raw key bytes |
| Expire at | 8 bytes | Milliseconds since Unix epoch, or `-1` for no TTL |
| Value type | 1 byte | See type codes |
| Value payload | variable | Type-specific encoding |

**Type codes**

| Code | Redis type | Payload |
|------|------------|---------|
| `0` | string | `length` + bytes |
| `1` | list | `count` + *count* × (`length` + bytes) in head→tail order |
| `2` | set | `count` + *count* × (`length` + bytes), sorted unsigned |
| `3` | hash | `field_count` + *count* × (field + value), fields sorted unsigned |
| `4` | sorted set | `count` + *count* × (`double64` score + member bytes), rank order |

Strings use Java `DataOutput` UTF-8 independent length-prefixed blobs (`writeInt` length, raw bytes). Doubles are IEEE754 big-endian.

## Commands

- **SAVE** — Blocks until a snapshot is written.
- **BGSAVE** — Starts a background snapshot; returns `ERR Background save already in progress` when one is already running.

Startup calls `load()` before accepting clients; shutdown `flush()` performs a blocking **SAVE**.

## Corruption detection

Load fails with a persistence error when:

- The file is truncated or has trailing garbage
- Magic or version does not match
- CRC32 does not match the body
- A value type code is unknown or lengths are negative

A missing `dump.myredis` is not an error; the server starts with an empty database.

## Durability guarantees

| Scenario | Guarantee |
|----------|-----------|
| Successful **SAVE** / **BGSAVE** / shutdown `flush` | After the atomic rename completes, a crash leaves either the previous complete snapshot or the new one—never a torn file. |
| Process crash before `flush` | In-memory changes since the last successful snapshot are lost. |
| **BGSAVE** during writes | Snapshot reflects a best-effort point-in-time; concurrent commands may interleave. |
| Expiration | TTL metadata is stored; expired keys are not written when captured via `KeyspaceSnapshotter` (lazy expiry on access). After load, passive/active expiry behave as usual. |
| AOF enabled | Snapshot is the bulk image; AOF carries incremental writes after the last `SAVE` baseline. See [RECOVERY.md](RECOVERY.md). |
| Replication | Not implemented. |

With AOF disabled, this is **snapshot-only** persistence: you can lose data written after the last successful save.
