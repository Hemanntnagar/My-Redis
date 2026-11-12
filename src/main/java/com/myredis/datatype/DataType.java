package com.myredis.datatype;

/**
 * Logical Redis types stored in the keyspace.
 */
public enum DataType {
    STRING,
    LIST,
    SET,
    SORTED_SET,
    HASH,
    STREAM;

    /** Redis {@code TYPE} command name for this value kind. */
    public String redisTypeName() {
        return switch (this) {
            case STRING -> "string";
            case LIST -> "list";
            case SET -> "set";
            case SORTED_SET -> "zset";
            case HASH -> "hash";
            case STREAM -> "stream";
        };
    }
}
