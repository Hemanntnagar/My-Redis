package com.myredis.storage;

import com.myredis.datatype.RedisValue;
import com.myredis.exception.StorageException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory {@link KeyValueStore} backed by a concurrent hash map.
 */
public final class InMemoryKeyValueStore implements KeyValueStore {

    private final Map<BinaryKey, RedisValue> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<RedisValue> get(byte[] key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(entries.get(new BinaryKey(key)));
    }

    @Override
    public void put(byte[] key, RedisValue value) throws StorageException {
        if (key == null) {
            throw new StorageException("ERR invalid key");
        }
        if (value == null) {
            throw new StorageException("ERR invalid value");
        }
        entries.put(new BinaryKey(key), value);
    }

    @Override
    public boolean delete(byte[] key) {
        Objects.requireNonNull(key, "key");
        return entries.remove(new BinaryKey(key)) != null;
    }

    @Override
    public boolean exists(byte[] key) {
        Objects.requireNonNull(key, "key");
        return entries.containsKey(new BinaryKey(key));
    }

    @Override
    public long keyCount() {
        return entries.size();
    }

    @Override
    public void clear() {
        entries.clear();
    }

    @Override
    public List<byte[]> snapshotKeys() {
        List<byte[]> keys = new ArrayList<>(entries.size());
        for (BinaryKey key : entries.keySet()) {
            keys.add(key.bytes().clone());
        }
        return keys;
    }

    /**
     * Map key that compares byte contents instead of array references.
     */
    private record BinaryKey(byte[] bytes) {

        private BinaryKey {
            bytes = bytes.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BinaryKey that && Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }
}
