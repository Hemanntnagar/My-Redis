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
import java.util.function.UnaryOperator;

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

    @Override
    public void rename(byte[] source, byte[] destination) throws StorageException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        if (Arrays.equals(source, destination)) {
            if (!exists(source)) {
                throw missingKey();
            }
            return;
        }
        BinaryKey from = new BinaryKey(source);
        BinaryKey to = new BinaryKey(destination);
        RedisValue[] moved = {null};
        entries.compute(
                from,
                (key, value) -> {
                    if (value == null) {
                        return null;
                    }
                    moved[0] = value;
                    return null;
                });
        if (moved[0] == null) {
            throw missingKey();
        }
        entries.put(to, moved[0]);
    }

    @Override
    public boolean renameIfAbsent(byte[] source, byte[] destination) throws StorageException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        if (Arrays.equals(source, destination)) {
            if (!exists(source)) {
                throw missingKey();
            }
            return false;
        }
        BinaryKey from = new BinaryKey(source);
        BinaryKey to = new BinaryKey(destination);
        if (entries.containsKey(to)) {
            if (!entries.containsKey(from)) {
                throw missingKey();
            }
            return false;
        }
        RedisValue[] moved = {null};
        entries.compute(
                from,
                (key, value) -> {
                    if (value == null) {
                        return null;
                    }
                    moved[0] = value;
                    return null;
                });
        if (moved[0] == null) {
            throw missingKey();
        }
        if (entries.putIfAbsent(to, moved[0]) == null) {
            return true;
        }
        entries.put(from, moved[0]);
        return false;
    }

    @Override
    public Optional<RedisValue> merge(byte[] key, UnaryOperator<RedisValue> remapper) throws StorageException {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(remapper, "remapper");
        BinaryKey binaryKey = new BinaryKey(key);
        RedisValue[] result = {null};
        entries.compute(
                binaryKey,
                (ignored, existing) -> {
                    RedisValue updated = remapper.apply(existing);
                    result[0] = updated;
                    return updated;
                });
        return Optional.ofNullable(result[0]);
    }

    private static StorageException missingKey() {
        return new StorageException("ERR no such key");
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
