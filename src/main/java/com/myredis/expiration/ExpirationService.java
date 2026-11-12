package com.myredis.expiration;

/**
 * Tracks expiring keys and removes them when deadlines elapse.
 */
public interface ExpirationService {

    void register(byte[] key, long expireAtEpochMillis);

    void cancel(byte[] key);

    boolean isExpired(byte[] key, long nowEpochMillis);
}
