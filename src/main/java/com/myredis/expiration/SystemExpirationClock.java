package com.myredis.expiration;

/**
 * {@link ExpirationClock} backed by {@link System#currentTimeMillis()}.
 */
public enum SystemExpirationClock implements ExpirationClock {
    INSTANCE;

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
