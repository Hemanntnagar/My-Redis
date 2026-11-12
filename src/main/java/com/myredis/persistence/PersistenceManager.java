package com.myredis.persistence;

import java.io.IOException;

/**
 * Loads and persists database state to disk.
 */
public interface PersistenceManager {

    void load() throws IOException;

    void flush() throws IOException;
}
