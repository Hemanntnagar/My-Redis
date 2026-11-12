package com.myredis.replication;

import com.myredis.network.ClientSession;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marks client sessions that have completed {@code REPLSYNC} and must not read further commands.
 */
public final class ReplicationSessionRegistry {

    private final Set<String> replicationSessions = ConcurrentHashMap.newKeySet();

    public void markReplicationClient(ClientSession session) {
        replicationSessions.add(session.id());
    }

    public boolean isReplicationClient(ClientSession session) {
        return replicationSessions.contains(session.id());
    }

    public void unmark(ClientSession session) {
        replicationSessions.remove(session.id());
    }
}
