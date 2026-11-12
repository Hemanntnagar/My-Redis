package com.myredis.storage;

import com.myredis.config.MaxMemoryPolicy;
import com.myredis.datatype.RedisValue;
import com.myredis.exception.StorageException;
import com.myredis.expiration.ExpirationService;
import com.myredis.metrics.AtomicMetricsRecorder;
import com.myredis.metrics.ServerStatistics;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;

/**
 * Enforces {@code maxmemory} with LRU eviction and keeps byte accounting consistent with the delegate.
 */
public final class EvictingKeyValueStore implements KeyValueStore {

    private final KeyValueStore delegate;
    private final MemoryUsageTracker memory;
    private final long maxMemoryBytes;
    private final MaxMemoryPolicy policy;
    private final ExpirationService expiration;
    private final AtomicMetricsRecorder metrics;
    private final LruEvictionIndex lru = new LruEvictionIndex();

    public EvictingKeyValueStore(
            KeyValueStore delegate,
            MemoryUsageTracker memory,
            long maxMemoryBytes,
            MaxMemoryPolicy policy,
            ExpirationService expiration,
            AtomicMetricsRecorder metrics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.memory = Objects.requireNonNull(memory, "memory");
        this.maxMemoryBytes = Math.max(0, maxMemoryBytes);
        this.policy = Objects.requireNonNull(policy, "policy");
        this.expiration = Objects.requireNonNull(expiration, "expiration");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public Optional<RedisValue> get(byte[] key) {
        Objects.requireNonNull(key, "key");
        synchronized (lru) {
            Optional<RedisValue> value = delegate.get(key);
            if (value.isPresent()) {
                lru.touch(key);
            }
            return value;
        }
    }

    @Override
    public void put(byte[] key, RedisValue value) throws StorageException {
        if (key == null) {
            throw new StorageException("ERR invalid key");
        }
        if (value == null) {
            throw new StorageException("ERR invalid value");
        }
        synchronized (lru) {
            Optional<RedisValue> existing = delegate.get(key);
            ensureWithinLimit(
                    () ->
                            memory.usedBytes()
                                    - (existing
                                            .map(v -> RedisValueMemoryEstimator.estimateKeyAndValue(key, v))
                                            .orElse(0L))
                                    + RedisValueMemoryEstimator.estimateKeyAndValue(key, value),
                    key);
            delegate.put(key, value);
            memory.recordPut(key, existing.orElse(null), value);
            lru.touch(key);
        }
    }

    @Override
    public boolean delete(byte[] key) {
        Objects.requireNonNull(key, "key");
        synchronized (lru) {
            Optional<RedisValue> existing = delegate.get(key);
            boolean removed = delegate.delete(key);
            if (removed) {
                memory.recordDelete(key, existing.orElse(null));
                lru.remove(key);
            }
            return removed;
        }
    }

    @Override
    public boolean exists(byte[] key) {
        return get(key).isPresent();
    }

    @Override
    public long keyCount() {
        return delegate.keyCount();
    }

    @Override
    public void clear() {
        synchronized (lru) {
            delegate.clear();
            memory.reset();
            lru.clear();
        }
    }

    @Override
    public List<byte[]> snapshotKeys() {
        return delegate.snapshotKeys();
    }

    @Override
    public void rename(byte[] source, byte[] destination) throws StorageException {
        synchronized (lru) {
            Optional<RedisValue> src = delegate.get(source);
            Optional<RedisValue> dst = delegate.get(destination);
            delegate.rename(source, destination);
            if (src.isPresent()) {
                memory.recordDelete(source, src.get());
                lru.remove(source);
            }
            if (dst.isPresent()) {
                memory.recordDelete(destination, dst.get());
            }
            if (src.isPresent()) {
                memory.recordPut(destination, null, src.get());
                lru.touch(destination);
            }
        }
    }

    @Override
    public boolean renameIfAbsent(byte[] source, byte[] destination) throws StorageException {
        synchronized (lru) {
            Optional<RedisValue> src = delegate.get(source);
            boolean renamed = delegate.renameIfAbsent(source, destination);
            if (renamed && src.isPresent()) {
                memory.recordDelete(source, src.get());
                lru.remove(source);
                memory.recordPut(destination, null, src.get());
                lru.touch(destination);
            }
            return renamed;
        }
    }

  @Override
    public Optional<RedisValue> merge(byte[] key, UnaryOperator<RedisValue> remapper) throws StorageException {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(remapper, "remapper");
        synchronized (lru) {
            Optional<RedisValue> existing = delegate.get(key);
            RedisValue updated = remapper.apply(existing.orElse(null));
            if (updated == null) {
                if (existing.isPresent()) {
                    delete(key);
                }
                return Optional.empty();
            }
            ensureWithinLimit(
                    () ->
                            memory.usedBytes()
                                    - existing
                                            .map(v -> RedisValueMemoryEstimator.estimateKeyAndValue(key, v))
                                            .orElse(0L)
                                    + RedisValueMemoryEstimator.estimateKeyAndValue(key, updated),
                    key);
            if (updated == null) {
                if (existing.isPresent()) {
                    delete(key);
                }
                return Optional.empty();
            }
            delegate.put(key, updated);
            memory.recordPut(key, existing.orElse(null), updated);
            lru.touch(key);
            return Optional.of(updated);
        }
    }

    private void ensureWithinLimit(LongSupplier projectedSupplier, byte[] insertKey) throws StorageException {
        if (maxMemoryBytes == 0) {
            return;
        }
        long projectedBytes = projectedSupplier.getAsLong();
        if (policy == MaxMemoryPolicy.NOEVICTION) {
            if (projectedBytes > maxMemoryBytes) {
                throw new StorageException("OOM command not allowed when used memory > 'maxmemory'");
            }
            return;
        }
        while (projectedBytes > maxMemoryBytes) {
            Optional<byte[]> victim =
                    lru.oldestEligible(
                            key -> eligibleForEviction(key) && !Arrays.equals(key, insertKey));
            if (victim.isEmpty()) {
                throw new StorageException("OOM command not allowed when used memory > 'maxmemory'");
            }
            byte[] evictedKey = victim.get();
            Optional<RedisValue> value = delegate.get(evictedKey);
            if (value.isEmpty()) {
                lru.remove(evictedKey);
                continue;
            }
            expiration.cancel(evictedKey);
            delegate.delete(evictedKey);
            memory.recordDelete(evictedKey, value.get());
            lru.remove(evictedKey);
            metrics.increment(ServerStatistics.METRIC_EVICTED_KEYS);
            projectedBytes = projectedSupplier.getAsLong();
        }
    }

    private boolean eligibleForEviction(byte[] key) {
        return switch (policy) {
            case ALLKEYS_LRU -> true;
            case VOLATILE_LRU -> expiration.findMetadata(key).isPresent();
            case NOEVICTION -> false;
        };
    }
}
