package com.myredis.expiration;

/**
 * Injectable time source for expiration and TTL calculations.
 */
public interface ExpirationClock {

    long currentTimeMillis();
}
