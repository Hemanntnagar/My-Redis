package com.myredis.config;

/**
 * Eviction behavior when {@link ServerConfiguration#maxMemoryBytes()} is exceeded.
 */
public enum MaxMemoryPolicy {
    NOEVICTION,
    ALLKEYS_LRU,
    VOLATILE_LRU
}
