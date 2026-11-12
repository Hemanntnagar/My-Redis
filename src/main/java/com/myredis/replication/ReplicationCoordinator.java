package com.myredis.replication;

import com.myredis.network.ClientSession;
import com.myredis.network.ConnectedClientSession;

import java.util.List;
import java.util.Optional;

/**
 * Role, replication offset, and replica connection management.
 */
public interface ReplicationCoordinator {

    ReplicationRole role();

    long replicationOffset();

    int connectedReplicaCount();

    /**
     * Appends a write to the replication backlog and fans it out to registered replicas.
     */
    void propagateWrite(List<byte[]> commandParts);

    /**
     * Performs full or partial synchronization for an inbound replica connection.
     */
    ReplicationSyncResult synchronizeReplica(ConnectedClientSession replicaSession, long replicaOffset);

    void registerInboundReplica(ConnectedClientSession session);

    void unregisterReplica(ConnectedClientSession session);

    Optional<String> replicaMasterAddress();

    void configureAsReplica(String masterHost, int masterPort);

    void configureAsMaster();

    /** {@code true} when the session completed {@code REPLSYNC} and only receives pushed writes. */
    boolean holdsReplicationLink(ClientSession session);
}
