# Concurrency and locking

My-Redis serves each TCP connection on a virtual thread, so many commands may run against the same keyspace at once.

## Layers

| Layer | Mechanism | Purpose |
|-------|-----------|---------|
| `InMemoryKeyValueStore` | `ConcurrentHashMap` | Safe publication of keys and value references |
| `KeyStripedLock` (256 stripes) | `ReentrantLock` per stripe | Serialize operations that touch the same key |
| `ExpirationAwareKeyValueStore` | Striped locks on every key API | Lazy expiry, rename, and `merge` run under the correct key lock(s) |
| Collection values (`ListRedisValue`, `SetRedisValue`, …) | `synchronized` methods | In-place mutations on a shared value instance |
| `InMemoryExpirationService` | `ConcurrentHashMap` + `remove(key, expected)` | Expiration updates vs active expiry |
| `AppendOnlyFile` | `synchronized` `writeLock` | Single-writer append, fsync, and rewrite |
| Snapshot / BGSAVE | Background thread + atomic file rename | Point-in-time capture without blocking readers |

## Deadlock avoidance

- **Two-key operations** (`RENAME`, `RENAMENX`): acquire stripes in unsigned lexicographic key order.
- **No lock ordering** between unrelated keys: unrelated commands may proceed in parallel on different stripes.
- **Persistence**: AOF rewrite and snapshot save do not hold keyspace locks; they read via `KeyspaceSnapshotter` (best-effort point-in-time).

## Atomic updates

- **`merge` on `KeyValueStore`** — `ConcurrentHashMap.compute` (strings) plus striped locks (expiration-aware path) for `INCR`, `APPEND`, and list creation.
- **Read/modify/write on collections** — Hold the key stripe, then mutate the synchronized value object.

## Stress coverage

See `KeyspaceConcurrencyStressTest` (concurrent `INCR`, mixed get/set/del, active expiration) and `ActiveExpirationStressTest`.
