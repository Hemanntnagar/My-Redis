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

- AOF and replication are not implemented; durability is snapshot-only (see [docs/SNAPSHOT.md](docs/SNAPSHOT.md))
- Built-in commands include `SAVE`, `BGSAVE`, `TTL`, `PTTL`, `EXPIRE`, `PEXPIRE`, `EXPIREAT`, `PEXPIREAT`, `PERSIST`, plus strings, lists, sets, hashes, sorted sets, and keyspace commands (`PING`, `GET`, `SET`, `KEYS`, `DEL`, …)

## Manual check (redis-cli)

```bash
./mvnw -q -DskipTests package dependency:build-classpath -Dmdep.outputFile=target/cp.txt
MYREDIS_HOST=127.0.0.1 MYREDIS_PORT=6379 java -cp "target/classes:$(cat target/cp.txt)" com.myredis.JavaRedis
# in another terminal:
redis-cli -p 6379 PING
redis-cli -p 6379 ECHO hello
redis-cli -p 6379 SET mykey hello
redis-cli -p 6379 GET mykey
redis-cli -p 6379 GET no-such-key
redis-cli -p 6379 DEL mykey
redis-cli -p 6379 EXISTS mykey other
redis-cli -p 6379 TYPE mykey
redis-cli -p 6379 MSET k1 v1 k2 v2
redis-cli -p 6379 MGET k1 k2 missing
redis-cli -p 6379 APPEND k1 suffix
redis-cli -p 6379 INCR counter
redis-cli -p 6379 INCRBY counter 10
redis-cli -p 6379 STRLEN counter
redis-cli -p 6379 DBSIZE
redis-cli -p 6379 KEYS 'user:*'
redis-cli -p 6379 FLUSHDB
redis-cli -p 6379 RENAME oldkey newkey
redis-cli -p 6379 RANDOMKEY
redis-cli -p 6379 RPUSH mylist a b
redis-cli -p 6379 LRANGE mylist 0 -1
redis-cli -p 6379 SADD myset a b
redis-cli -p 6379 SMEMBERS myset
redis-cli -p 6379 HSET user name alice
redis-cli -p 6379 HGET user name
```
