package com.myredis.storage;

import com.myredis.datatype.RedisValue;
import com.myredis.exception.StorageException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Primary in-memory keyspace abstraction for a single logical database.
 */
public interface KeyValueStore {

    Optional<RedisValue> get(byte[] key);

    void put(byte[] key, RedisValue value) throws StorageException;

    boolean delete(byte[] key);

    boolean exists(byte[] key);

    /** Number of keys in this logical database. */
    long keyCount();

    /** Removes every key in this logical database. */
    void clear();

    /**
     * Point-in-time snapshot of all keys for pattern scanning; safe under concurrent mutations.
     */
    List<byte[]> snapshotKeys();

    /**
     * Moves the value at {@code source} to {@code destination}, overwriting an existing destination key.
     *
     * @throws StorageException when {@code source} is absent ({@code ERR no such key})
     */
    void rename(byte[] source, byte[] destination) throws StorageException;

    /**
     * Moves the value at {@code source} only when {@code destination} is absent.
     *
     * @return {@code true} when the rename succeeded, {@code false} when the destination key exists
     * @throws StorageException when {@code source} is absent ({@code ERR no such key})
     */
    boolean renameIfAbsent(byte[] source, byte[] destination) throws StorageException;

    /**
     * Atomically updates or inserts the value for {@code key}. {@code null} removes the key when present.
     */
    default Optional<RedisValue> merge(byte[] key, UnaryOperator<RedisValue> remapper) throws StorageException {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(remapper, "remapper");
        Optional<RedisValue> existing = get(key);
        RedisValue updated = remapper.apply(existing.orElse(null));
        if (updated == null) {
            if (existing.isPresent()) {
                delete(key);
            }
            return Optional.empty();
        }
        put(key, updated);
        return Optional.of(updated);
    }
}
