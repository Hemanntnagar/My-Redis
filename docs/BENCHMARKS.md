# Benchmarks

Repeatable TCP benchmarks for `GET`, `SET`, `INCR`, and a mixed workload (50% GET / 40% SET / 10% INCR).

## What is measured

| Metric | Description |
|--------|-------------|
| Throughput | Operations per second during the measurement window (after warmup) |
| Average latency | Mean round-trip time per operation (microseconds) |
| p50 / p95 / p99 | Percentiles from per-operation samples (up to 500k per scenario) |
| Memory | JVM heap estimate (`totalMemory - freeMemory`) after the scenario |
| Concurrency | Separate runs for each client count (default `1,4,16,32`) |

Benchmarks use an in-memory server with **AOF and replication disabled**, so results reflect command path + network I/O without persistence overhead.

## Run

```bash
./mvnw -q -DskipTests package dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java -cp "target/classes:$(cat target/cp.txt)" com.myredis.benchmark.BenchmarkMain
```

Outputs land in `benchmark-results/` as Markdown and JSON Lines with a timestamp.

### Options

```
--warmup-seconds 3
--duration-seconds 8
--keyspace 10000
--value-size 64
--clients 1,4,16,32
--results-dir benchmark-results
```

## Workload definitions

- **GET** — random keys from a preloaded space (`bench:key:0..N-1`).
- **SET** — random keys, fixed 64-byte payload (configurable).
- **INCR** — per-client key `bench:incr:<clientId>` (no cross-client contention by default).
- **MIXED** — probabilistic blend; keys preloaded like GET.

Warmup runs for the same duration as measurement; only post-warmup samples count toward latency percentiles and throughput.

## Optimization notes (2026-11-12)

Bottlenecks addressed after the initial benchmark pass:

1. **Object allocation (keyspace)** — map lookups used to allocate and clone keys on every `GET`/`EXISTS`. `ByteArrayKey.probe` performs content-keyed lookups without copying key bytes.
2. **Lock contention (expiration)** — `ExpirationAwareKeyValueStore` acquired a stripe lock on every read even when no TTL was set. Keys without expiration metadata now use a lock-free read path on the delegate store.
3. **INCR allocations** — integer parse/format no longer constructs intermediate `String` values (`AsciiLongCodec`).

Persistence and replication remain on the slow path by design; enable them only when durability semantics are required.

## Reference run (post-optimization, local JVM)

Configuration: `keyspace=5000`, `value-size=64`, `warmup=2s`, `duration=5s`, clients `1/4/16`. Full table written to `benchmark-results/benchmark-*.md`.

| Workload | Clients | Throughput (ops/s) | p99 (µs) |
|----------|---------|-------------------|----------|
| GET | 1 | 26,955 | 118.5 |
| GET | 16 | 75,631 | 551.3 |
| SET | 1 | 30,229 | 73.8 |
| SET | 16 | 81,658 | 402.6 |
| INCR | 1 | 29,776 | 68.3 |
| INCR | 16 | 69,574 | 547.4 |
| MIXED | 16 | 74,938 | 455.8 |

Throughput scales with client count up to the mid‑tens of thousands of ops/s on a single node for this workload size; tail latency (p99) grows with concurrency as virtual-thread clients contend on stripes and sockets.

## Comparing runs

Keep hardware, JDK, `--keyspace`, `--value-size`, and `--clients` identical. Compare `benchmark-*.md` files or JSONL lines for the same scenario label (e.g. `get_c16_keys10000`).

Example interpretation:

- Throughput rising with client count suggests good concurrency; flat or falling curves may indicate lock or I/O saturation.
- p99 growing faster than p50 often signals tail latency from contention (e.g. shared `INCR` with `--incr-shared` if added later).
