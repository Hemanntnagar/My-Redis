package com.myredis.persistence.snapshot;

import com.myredis.datatype.RedisValue;

import java.util.Objects;

/** One keyspace row captured for persistence. */
public record SnapshotEntry(byte[] key, RedisValue value, long expireAtEpochMillis) {

    public SnapshotEntry {
        key = Objects.requireNonNull(key, "key").clone();
        value = Objects.requireNonNull(value, "value");
    }
}
