package com.myredis.replication;

import com.myredis.network.ClientSession;
import com.myredis.network.ConnectedClientSession;
import com.myredis.persistence.SnapshotPersistenceManager;
import com.myredis.persistence.snapshot.DatabaseSnapshot;
import com.myredis.persistence.snapshot.KeyspaceSnapshotter;
import com.myredis.persistence.snapshot.SnapshotWriter;
import com.myredis.storage.ExpirationAwareKeyValueStore;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Primary-side replication offset, backlog, and replica fan-out.
 */
public final class InMemoryReplicationCoordinator implements ReplicationCoordinator {

    private final ExpirationAwareKeyValueStore store;
    private final SnapshotPersistenceManager snapshot;
    private final ReplicationBacklog backlog;
    private final ReplicaStreamSession replicaSessions = new ReplicaStreamSession();
    private final ReplicationSessionRegistry replicationSessions = new ReplicationSessionRegistry();
    private volatile ReplicationRole role = ReplicationRole.MASTER;
    private final AtomicReference<String> masterHost = new AtomicReference<>();
    private final AtomicReference<Integer> masterPort = new AtomicReference<>(0);

    public InMemoryReplicationCoordinator(
            ExpirationAwareKeyValueStore store,
            SnapshotPersistenceManager snapshot,
            int backlogCapacity) {
        this.store = Objects.requireNonNull(store, "store");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.backlog = new ReplicationBacklog(backlogCapacity);
    }

    @Override
    public ReplicationRole role() {
        return role;
    }

    @Override
    public long replicationOffset() {
        synchronized (backlog) {
            return backlog.masterOffset();
        }
    }

    @Override
    public int connectedReplicaCount() {
        return replicaSessions.connectedReplicaCount();
    }

    @Override
    public void propagateWrite(List<byte[]> commandParts) {
        if (role != ReplicationRole.MASTER) {
            return;
        }
        byte[] encoded = ReplicaStreamSession.encodeCommandParts(commandParts);
        long offset;
        synchronized (backlog) {
            offset = backlog.append(encoded);
        }
        replicaSessions.broadcast(encoded);
        // offset is visible through replicationOffset(); payload already pushed.
        if (offset < 0) {
            throw new IllegalStateException("invalid replication offset");
        }
    }

    @Override
    public ReplicationSyncResult synchronizeReplica(ConnectedClientSession replicaSession, long replicaOffset) {
        Objects.requireNonNull(replicaSession, "replicaSession");
        registerInboundReplica(replicaSession);
        synchronized (backlog) {
            long masterOffset = backlog.masterOffset();
            if (replicaOffset > 0 && replicaOffset == masterOffset) {
                return new ReplicationSyncResult.AlreadySynced(masterOffset);
            }
            if (backlog.canPartialSync(replicaOffset)) {
                List<byte[]> replay = backlog.commandsAfter(replicaOffset);
                for (byte[] encoded : replay) {
                    replicaSessions.sendRaw(replicaSession, ReplicaStreamSession.wrapReplicationFrame(encoded));
                }
                return new ReplicationSyncResult.PartialSync(masterOffset, replay.size());
            }
            if (store.keyCount() > 0) {
                DatabaseSnapshot databaseSnapshot =
                        KeyspaceSnapshotter.capture(store, store.expirationService());
                byte[] snapshotBytes;
                try {
                    snapshotBytes = SnapshotWriter.encode(databaseSnapshot);
                } catch (com.myredis.exception.PersistenceException failure) {
                    throw new IllegalStateException("failed to encode replication snapshot", failure);
                }
                replicaSessions.sendRaw(replicaSession, ReplicaStreamSession.bulkSnapshotMarker(snapshotBytes));
            }
            return new ReplicationSyncResult.FullSync(masterOffset);
        }
    }

    @Override
    public void registerInboundReplica(ConnectedClientSession session) {
        session.markReplicaLink();
        replicationSessions.markReplicationClient(session);
        replicaSessions.register(session);
    }

    @Override
    public void unregisterReplica(ConnectedClientSession session) {
        replicationSessions.unmark(session);
        replicaSessions.unregister(session);
    }

    @Override
    public boolean holdsReplicationLink(ClientSession session) {
        return replicationSessions.isReplicationClient(session);
    }

    @Override
    public Optional<String> replicaMasterAddress() {
        String host = masterHost.get();
        Integer port = masterPort.get();
        if (host == null || host.isBlank() || port == null || port <= 0) {
            return Optional.empty();
        }
        return Optional.of(host + ":" + port);
    }

    @Override
    public void configureAsReplica(String masterHost, int masterPort) {
        this.masterHost.set(masterHost);
        this.masterPort.set(masterPort);
        role = ReplicationRole.REPLICA;
    }

    @Override
    public void configureAsMaster() {
        masterHost.set(null);
        masterPort.set(0);
        role = ReplicationRole.MASTER;
    }

    public ExpirationAwareKeyValueStore store() {
        return store;
    }

    public SnapshotPersistenceManager snapshot() {
        return snapshot;
    }
}
