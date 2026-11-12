package com.myredis.storage;

import com.myredis.datatype.RedisValue;
import com.myredis.exception.StorageException;

import java.util.Optional;

/**
 * Primary in-memory keyspace abstraction for a single logical database.
 */
public interface KeyValueStore {

    Optional<RedisValue> get(byte[] key);

    void put(byte[] key, RedisValue value) throws StorageException;

    boolean delete(byte[] key);

    boolean exists(byte[] key);
}
