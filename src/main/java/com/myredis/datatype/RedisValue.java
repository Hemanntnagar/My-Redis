package com.myredis.datatype;

/**
 * Marker for a value stored under a key.
 */
public interface RedisValue {

    DataType type();
}
