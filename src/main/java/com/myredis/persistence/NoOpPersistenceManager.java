package com.myredis.persistence;

import com.myredis.exception.PersistenceException;

/**
 * Persistence hook with no disk I/O until RDB/AOF support is added.
 */
public final class NoOpPersistenceManager implements PersistenceManager {

    @Override
    public void load() throws PersistenceException {
        // intentionally empty
    }

    @Override
    public void flush() throws PersistenceException {
        // intentionally empty
    }
}
