package com.myredis.persistence;

import com.myredis.exception.PersistenceException;

/**
 * Loads and persists database state to disk.
 */
public interface PersistenceManager {

    void load() throws PersistenceException;

    void flush() throws PersistenceException;
}
