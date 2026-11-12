package com.myredis.config;

/**
 * Read-only view of server settings used during bootstrap.
 */
public interface ServerConfiguration {

    String bindAddress();

    int port();

    int tcpBacklog();
}
