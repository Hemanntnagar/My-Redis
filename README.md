# My-Redis

Redis-inspired in-memory database server (Java 26, Maven, RESP2).

## Package architecture

| Package | Responsibility |
|---------|----------------|
| `com.myredis.server` | Process lifecycle: bind, accept clients, graceful shutdown |
| `com.myredis.network` | TCP/NIO transport and per-client session state |
| `com.myredis.protocol` | RESP2 encoding and decoding |
| `com.myredis.command` | Command definitions and execution pipeline |
| `com.myredis.storage` | In-memory keyspace and database index management |
| `com.myredis.datatype` | Redis value types and in-memory representations |
| `com.myredis.persistence` | RDB/AOF-style durability hooks |
| `com.myredis.expiration` | TTL and active/passive key expiry |
| `com.myredis.transaction` | MULTI/EXEC/WATCH/DISCARD semantics |
| `com.myredis.replication` | Master/replica coordination |
| `com.myredis.pubsub` | Channel publish/subscribe |
| `com.myredis.config` | Server configuration (bind, port, persistence flags) |
| `com.myredis.metrics` | Operational counters and observability hooks |
| `com.myredis.exception` | Typed errors surfaced to clients and callers |
| `com.myredis.logging` | Structured SLF4J logging for operators |

Each package currently exposes `package-info.java` and a small set of foundational interfaces. Implementations are added in later phases.

## Build and test

```bash
./mvnw test
```


## Limitations

- No networking, storage engine, or command implementations yet
