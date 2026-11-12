package com.myredis.datatype;

import java.util.Arrays;
import java.util.Objects;

/**
 * Binary-safe string value stored in the keyspace.
 */
public final class StringRedisValue implements RedisValue {

    private final byte[] bytes;

    public StringRedisValue(byte[] bytes) {
        this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
    }

    @Override
    public DataType type() {
        return DataType.STRING;
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StringRedisValue that)) {
            return false;
        }
        return Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
