# Primary–replica replication

My-Redis implements a lightweight primary/replica protocol intended for standalone
deployments and integration tests. It is inspired by Redis replication but is **not**
wire-compatible with Redis `PSYNC` / `REPLCONF`.

## Roles

- **Primary** (`role:master` in `INFO replication`): accepts writes, maintains a
  monotonic `master_repl_offset`, and fans out encoded write commands to replicas.
- **Replica** (`role:slave`): read-only for normal clients; applies the replication
  stream through the same command path used for AOF recovery.

Configure at startup:

- `MYREDIS_REPLICA_MASTER_HOST` / `MYREDIS_REPLICA_MASTER_PORT` — start as a replica.
- Or run `REPLICAOF host port` at runtime (`REPLICAOF NO ONE` promotes back to primary).

## Handshake (`REPLSYNC`)

1. Replica opens an outbound connection and sends `REPLSYNC <offset>`.
2. Primary responds with either:
   - **Full sync**: `+FULLSYNC <offset>`, a snapshot bulk payload, then `+OK <offset>`.
   - **Partial sync**: `+PARTIALSYNC <offset> <replayed>`, backlog commands, then `+OK <offset>`.
3. After the handshake, the primary pushes each write as a RESP array wrapping one AOF-encoded
   command frame.

Offsets are tracked in an in-memory backlog (`MYREDIS_REPLICATION_BACKLOG_SIZE`, default 1024).
If the replica offset falls outside the backlog, a new full sync is performed.

## Write propagation

Mutating commands classified by `WriteCommandClassifier` increment the replication offset,
append to the backlog, and are broadcast to all registered replica sockets.

## Connection teardown

`ReplicationStreamReader` treats a closed or interrupted upstream socket as end-of-stream
(`Socket closed`, `Connection reset`, `Closed by interrupt`) and returns `null` instead of
surfacing an uncaught `SocketException` on replica follower threads. This matches blocking
`InputStream` behavior when the peer closes the TCP connection.

## Client commands

| Command | Purpose |
|---------|---------|
| `REPLICAOF host port` | Point this node at a primary |
| `REPLICAOF NO ONE` | Become a primary |
| `REPLSYNC offset` | Replication handshake (normally used by the upstream client) |
| `INFO replication` | Role, offset, connected replica count |

## Limitations

- No Redis-compatible `PSYNC`, `SYNC`, or `REPLCONF` support.
- Replication is **asynchronous**; replicas can lag behind the primary.
- Only commands in `WriteCommandClassifier` replicate; pub/sub, transactions, and
  admin-side effects are not fully replicated.
- Full sync transfers an encoded snapshot, not a live RDB file stream.
- Failover and replica promotion are manual (`REPLICAOF NO ONE`); no automatic election.
- Cross-datacenter security (TLS, ACL authentication on replication links) is not implemented.
- Backlog is fixed-size and in-memory; long outages always trigger full resynchronization.
