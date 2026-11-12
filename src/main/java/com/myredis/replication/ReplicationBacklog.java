package com.myredis.replication;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ring buffer of encoded replication commands keyed by monotonic offset.
 */
public final class ReplicationBacklog {

    private final int capacity;
    private final List<Entry> entries = new ArrayList<>();
    private long firstOffset = 1;
    private long nextOffset = 1;

    public ReplicationBacklog(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.capacity = capacity;
    }

    public synchronized long append(byte[] encodedCommand) {
        Objects.requireNonNull(encodedCommand, "encodedCommand");
        long offset = nextOffset++;
        entries.add(new Entry(offset, encodedCommand.clone()));
        if (entries.size() > capacity) {
            entries.removeFirst();
            firstOffset = entries.getFirst().offset();
        }
        return offset;
    }

    public synchronized long masterOffset() {
        return nextOffset - 1;
    }

    public synchronized boolean canPartialSync(long replicaOffset) {
        if (replicaOffset <= 0) {
            return false;
        }
        if (entries.isEmpty()) {
            return replicaOffset == masterOffset();
        }
        return replicaOffset >= firstOffset && replicaOffset < nextOffset;
    }

    public synchronized List<byte[]> commandsAfter(long replicaOffset) {
        List<byte[]> replay = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.offset() > replicaOffset) {
                replay.add(entry.encodedCommand());
            }
        }
        return replay;
    }

    private record Entry(long offset, byte[] encodedCommand) {}
}
