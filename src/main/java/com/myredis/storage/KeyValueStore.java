package com.myredis.storage;

import com.myredis.datatype.RedisValue;
import com.myredis.exception.StorageException;

import java.util.List;
import java.util.Optional;

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
}
