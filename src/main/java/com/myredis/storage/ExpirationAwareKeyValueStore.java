package com.myredis.storage;

import com.myredis.datatype.RedisValue;
import com.myredis.exception.StorageException;
import com.myredis.expiration.ExpirationClock;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.expiration.SystemExpirationClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

/**
 * {@link KeyValueStore} decorator that keeps expiration metadata in sync and purges expired keys on access.
 * Uses striped key locks so concurrent commands do not observe torn list/set/hash mutations.
 */
public final class ExpirationAwareKeyValueStore implements KeyValueStore {

    private final KeyValueStore delegate;
    private final InMemoryExpirationService expiration;
    private final ExpirationClock clock;
    private final KeyStripedLock keyLocks = new KeyStripedLock();

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

  public KeyStripedLock keyLocks() {
        return keyLocks;
    }

    @Override
    public Optional<RedisValue> get(byte[] key) {
        return keyLocks.callExclusiveReturning(key, () -> {
            purgeIfExpired(key);
            return delegate.get(key);
        });
    }

    @Override
    public void put(byte[] key, RedisValue value) throws StorageException {
        runExclusivePropagatingStorage(key, () -> {
            purgeIfExpired(key);
            delegate.put(key, value);
        });
    }

    @Override
    public boolean delete(byte[] key) {
        return keyLocks.callExclusiveReturning(
                key,
                () -> {
                    expiration.cancel(key);
                    return delegate.delete(key);
                });
    }

    @Override
    public boolean exists(byte[] key) {
        return keyLocks.callExclusiveReturning(
                key,
                () -> {
                    purgeIfExpired(key);
                    return delegate.exists(key);
                });
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
        List<byte[]> keys = delegate.snapshotKeys();
        List<byte[]> live = new ArrayList<>(keys.size());
        for (byte[] key : keys) {
            keyLocks.runExclusive(key, () -> {
                purgeIfExpired(key);
                if (delegate.exists(key)) {
                    live.add(key);
                }
            });
        }
        return live;
    }

    @Override
    public void rename(byte[] source, byte[] destination) throws StorageException {
        runExclusiveTwoKeysPropagatingStorage(
                source,
                destination,
                () -> {
                    purgeIfExpired(source);
                    purgeIfExpired(destination);
                    delegate.rename(source, destination);
                    expiration.transfer(source, destination);
                });
    }

    @Override
    public boolean renameIfAbsent(byte[] source, byte[] destination) throws StorageException {
        boolean[] renamed = {false};
        runExclusiveTwoKeysPropagatingStorage(
                source,
                destination,
                () -> {
                    purgeIfExpired(source);
                    purgeIfExpired(destination);
                    renamed[0] = delegate.renameIfAbsent(source, destination);
                    if (renamed[0]) {
                        expiration.transfer(source, destination);
                    }
                });
        return renamed[0];
    }

    @Override
    public Optional<RedisValue> merge(byte[] key, UnaryOperator<RedisValue> remapper) throws StorageException {
        Optional<RedisValue>[] holder = new Optional[] {Optional.empty()};
        runExclusivePropagatingStorage(
                key,
                () -> {
                    purgeIfExpired(key);
                    holder[0] = delegate.merge(key, remapper);
                });
        return holder[0];
    }

    void purgeIfExpired(byte[] key) {
        long now = clock.currentTimeMillis();
        if (expiration.tryRemoveIfExpired(key, now)) {
            delegate.delete(key);
        }
    }

    /**
     * Active expiration: scans a bounded number of metadata entries and deletes expired keys.
     *
     * @return how many keys were removed in this cycle
     */
    public int activeExpireCycle(int maxScans, int maxDeletes) {
        if (maxScans < 1 || maxDeletes < 1) {
            return 0;
        }
        long now = clock.currentTimeMillis();
        int[] deleted = {0};
        AtomicBoolean stop = new AtomicBoolean();
        expiration.scanMetadata(
                maxScans,
                (key, metadata) -> {
                    if (stop.get()) {
                        return;
                    }
                    if (!metadata.isExpiredAt(now)) {
                        return;
                    }
                    keyLocks.runExclusive(
                            key,
                            () -> {
                                if (expiration.tryRemoveIfExpired(key, now)) {
                                    delegate.delete(key);
                                    deleted[0]++;
                                    if (deleted[0] >= maxDeletes) {
                                        stop.set(true);
                                    }
                                }
                            });
                });
        return deleted[0];
    }

    private <T> T callExclusiveReturning(byte[] key, java.util.concurrent.Callable<T> action) {
        try {
            return keyLocks.callExclusive(key, action);
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private void runExclusivePropagatingStorage(byte[] key, StorageRunnable action) throws StorageException {
        try {
            keyLocks.callExclusive(
                    key,
                    () -> {
                        try {
                            action.run();
                        } catch (StorageException failure) {
                            throw new StoragePropagation(failure);
                        }
                        return null;
                    });
        } catch (StoragePropagation failure) {
            throw failure.cause();
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new StorageException("ERR storage operation failed: " + failure.getMessage());
        }
    }

    private void runExclusiveTwoKeysPropagatingStorage(byte[] first, byte[] second, StorageRunnable action)
            throws StorageException {
        try {
            keyLocks.runExclusive(
                    first,
                    second,
                    () -> {
                        try {
                            action.run();
                        } catch (StorageException failure) {
                            throw new StoragePropagation(failure);
                        }
                    });
        } catch (StoragePropagation failure) {
            throw failure.cause();
        }
    }

    @FunctionalInterface
    private interface StorageRunnable {
        void run() throws StorageException;
    }

    private static final class StoragePropagation extends RuntimeException {
        private final StorageException storageFailure;

        private StoragePropagation(StorageException storageFailure) {
            super(storageFailure);
            this.storageFailure = storageFailure;
        }

        private StorageException cause() {
            return storageFailure;
        }
    }
}
