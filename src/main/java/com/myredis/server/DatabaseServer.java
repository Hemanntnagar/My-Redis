package com.myredis.server;

import com.myredis.exception.ConnectionException;

/**
 * Lifecycle of the My-Redis listener process.
 */
public interface DatabaseServer extends AutoCloseable {

    void start() throws ConnectionException;

    void stop();

    @Override
    void close();
}
