package com.myredis.replication;

/**
 * Outcome of a {@code REPLSYNC} handshake on the primary.
 */
public sealed interface ReplicationSyncResult {

    record FullSync(long masterOffset) implements ReplicationSyncResult {}

    record PartialSync(long masterOffset, int replayedCommands) implements ReplicationSyncResult {}

    record AlreadySynced(long masterOffset) implements ReplicationSyncResult {}
}
