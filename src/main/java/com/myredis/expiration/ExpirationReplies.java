package com.myredis.expiration;

/**
 * Sentinel integer replies for TTL-family commands (Redis-compatible).
 */
public final class ExpirationReplies {

    public static final long KEY_MISSING = -2L;
    public static final long NO_EXPIRE = -1L;

    private ExpirationReplies() {}
}
