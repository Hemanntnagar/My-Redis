package com.myredis.storage;

import com.myredis.datatype.RedisValue;
import com.myredis.exception.StorageException;
import com.myredis.transaction.WatchRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Notifies a {@link WatchRegistry} when keys are created, updated, or removed so {@code WATCH} can abort {@code EXEC}.
 */
public final class WatchNotifyingKeyValueStore implements KeyValueStore {

    private final KeyValueStore delegate;
    private final WatchRegistry watchRegistry;

    public WatchNotifyingKeyValueStore(KeyValueStore delegate, WatchRegistry watchRegistry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.watchRegistry = Objects.requireNonNull(watchRegistry, "watchRegistry");
    }

    @Override
    public Optional<RedisValue> get(byte[] key) {
        return delegate.get(key);
    }

    @Override
    public void put(byte[] key, RedisValue value) throws StorageException {
        delegate.put(key, value);
        watchRegistry.keyModified(key);
    }

    @Override
    public boolean delete(byte[] key) {
        boolean removed = delegate.delete(key);
        if (removed) {
            watchRegistry.keyModified(key);
        }
        return removed;
    }

    @Override
    public boolean exists(byte[] key) {
        return delegate.exists(key);
    }

    @Override
    public long keyCount() {
        return delegate.keyCount();
    }

    @Override
    public void clear() {
        List<byte[]> keys = delegate.snapshotKeys();
        delegate.clear();
        for (byte[] key : keys) {
            watchRegistry.keyModified(key);
        }
    }

    @Override
    public List<byte[]> snapshotKeys() {
        return delegate.snapshotKeys();
    }

    @Override
    public void rename(byte[] source, byte[] destination) throws StorageException {
        delegate.rename(source, destination);
        watchRegistry.keyModified(source);
        watchRegistry.keyModified(destination);
    }

    @Override
    public boolean renameIfAbsent(byte[] source, byte[] destination) throws StorageException {
        boolean renamed = delegate.renameIfAbsent(source, destination);
        if (renamed) {
            watchRegistry.keyModified(source);
            watchRegistry.keyModified(destination);
        }
        return renamed;
    }

    @Override
    public Optional<RedisValue> merge(byte[] key, UnaryOperator<RedisValue> remapper) throws StorageException {
        Optional<RedisValue> before = delegate.get(key);
        Optional<RedisValue> result = delegate.merge(key, remapper);
        if (before.isPresent() && result.isEmpty()) {
            watchRegistry.keyModified(key);
        } else if (result.isPresent()) {
            watchRegistry.keyModified(key);
        }
        return result;
    }
}
