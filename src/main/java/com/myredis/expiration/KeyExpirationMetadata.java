package com.myredis.expiration;

/**
 * Deadline metadata associated with a key that has a time-to-live.
 */
public record KeyExpirationMetadata(long expireAtEpochMillis) {

    public boolean isExpiredAt(long nowEpochMillis) {
        return nowEpochMillis >= expireAtEpochMillis;
    }

    /** Remaining lifetime in milliseconds, floored at zero. */
    public long remainingMillisAt(long nowEpochMillis) {
        return Math.max(0L, expireAtEpochMillis - nowEpochMillis);
    }

    /** Redis-style TTL seconds: sub-second remainder counts as one second. */
    public long remainingSecondsAt(long nowEpochMillis) {
        long remaining = remainingMillisAt(nowEpochMillis);
        if (remaining == 0L) {
            return 0L;
        }
        return (remaining + 999L) / 1000L;
    }
}
