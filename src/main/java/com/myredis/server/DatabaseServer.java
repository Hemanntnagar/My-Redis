package com.myredis.server;

import java.io.IOException;

/**
 * Lifecycle of the My-Redis listener process.
 */
public interface DatabaseServer extends AutoCloseable {

    void start() throws IOException;

    void stop();

    @Override
    void close();
}
