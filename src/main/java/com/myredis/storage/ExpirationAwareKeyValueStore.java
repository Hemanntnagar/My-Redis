package com.myredis.storage;

import com.myredis.datatype.RedisValue;
import com.myredis.exception.StorageException;
import com.myredis.expiration.ExpirationClock;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.expiration.SystemExpirationClock;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link KeyValueStore} decorator that keeps expiration metadata in sync and purges expired keys on access.
 */
public final class ExpirationAwareKeyValueStore implements KeyValueStore {

    private final KeyValueStore delegate;
    private final InMemoryExpirationService expiration;
    private final ExpirationClock clock;

    public ExpirationAwareKeyValueStore(KeyValueStore delegate, InMemoryExpirationService expiration) {
        this(delegate, expiration, SystemExpirationClock.INSTANCE);
    }

    public ExpirationAwareKeyValueStore(
            KeyValueStore delegate, InMemoryExpirationService expiration, ExpirationClock clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.expiration = Objects.requireNonNull(expiration, "expiration");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public InMemoryExpirationService expirationService() {
        return expiration;
    }

    @Override
    public Optional<RedisValue> get(byte[] key) {
        purgeIfExpired(key);
        return delegate.get(key);
    }

    @Override
    public void put(byte[] key, RedisValue value) throws StorageException {
        delegate.put(key, value);
    }

    @Override
    public boolean delete(byte[] key) {
        expiration.cancel(key);
        return delegate.delete(key);
    }

    @Override
    public boolean exists(byte[] key) {
        purgeIfExpired(key);
        return delegate.exists(key);
    }

    @Override
    public long keyCount() {
        return delegate.keyCount();
    }

    @Override
    public void clear() {
        delegate.clear();
        expiration.clearAll();
    }

    @Override
    public List<byte[]> snapshotKeys() {
        return delegate.snapshotKeys();
    }

    @Override
    public void rename(byte[] source, byte[] destination) throws StorageException {
        delegate.rename(source, destination);
        expiration.transfer(source, destination);
    }

    @Override
    public boolean renameIfAbsent(byte[] source, byte[] destination) throws StorageException {
        boolean renamed = delegate.renameIfAbsent(source, destination);
        if (renamed) {
            expiration.transfer(source, destination);
        }
        return renamed;
    }

    void purgeIfExpired(byte[] key) {
        long now = clock.currentTimeMillis();
        if (!expiration.isExpired(key, now)) {
            return;
        }
        if (delegate.exists(key)) {
            delegate.delete(key);
        }
        expiration.cancel(key);
    }
}
