package com.myredis.expiration;

/**
 * Tunables for the background expiration janitor.
 */
public record ExpirationCleanupPolicy(
        long tickMillis,
        long idleBackoffMillis,
        int maxKeysScannedPerTick,
        int maxKeysDeletedPerTick) {

    private static final long DEFAULT_TICK_MILLIS = 100L;
    private static final long DEFAULT_IDLE_BACKOFF_MILLIS = 500L;
    private static final int DEFAULT_MAX_SCANNED = 64;
    private static final int DEFAULT_MAX_DELETED = 32;

    public ExpirationCleanupPolicy {
        if (tickMillis < 1L) {
            throw new IllegalArgumentException("tickMillis must be positive");
        }
        if (idleBackoffMillis < tickMillis) {
            throw new IllegalArgumentException("idleBackoffMillis must be at least tickMillis");
        }
        if (maxKeysScannedPerTick < 1) {
            throw new IllegalArgumentException("maxKeysScannedPerTick must be positive");
        }
        if (maxKeysDeletedPerTick < 1) {
            throw new IllegalArgumentException("maxKeysDeletedPerTick must be positive");
        }
    }

    public static ExpirationCleanupPolicy defaults() {
        return new ExpirationCleanupPolicy(
                DEFAULT_TICK_MILLIS,
                DEFAULT_IDLE_BACKOFF_MILLIS,
                DEFAULT_MAX_SCANNED,
                DEFAULT_MAX_DELETED);
    }
}
