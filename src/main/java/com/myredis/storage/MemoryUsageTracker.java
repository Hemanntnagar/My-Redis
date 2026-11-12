package com.myredis.storage;

import com.myredis.datatype.RedisValue;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks approximate keyspace bytes for {@code maxmemory} decisions.
 */
public final class MemoryUsageTracker {

    private final AtomicLong usedBytes = new AtomicLong();

    public long usedBytes() {
        return usedBytes.get();
    }

    public void recordPut(byte[] key, RedisValue previous, RedisValue next) {
        if (previous != null) {
            usedBytes.addAndGet(-RedisValueMemoryEstimator.estimateKeyAndValue(key, previous));
        }
        if (next != null) {
            usedBytes.addAndGet(RedisValueMemoryEstimator.estimateKeyAndValue(key, next));
        }
    }

    public void recordDelete(byte[] key, RedisValue removed) {
        if (removed != null) {
            usedBytes.addAndGet(-RedisValueMemoryEstimator.estimateKeyAndValue(key, removed));
        }
    }

    public void reset() {
        usedBytes.set(0);
    }
}
