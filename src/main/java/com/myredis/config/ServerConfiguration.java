package com.myredis.config;

import com.myredis.exception.InvalidConfigurationException;

/**
 * Read-only view of server settings used during bootstrap.
 */
public interface ServerConfiguration {

    /**
     * Address the server binds for client connections (e.g. {@code 0.0.0.0}, {@code 127.0.0.1}).
     */
    String host();

    /**
     * TCP listen port; default {@value ServerConfigurationDefaults#PORT}.
     */
    int port();

    /**
     * Upper bound on simultaneous client connections.
     */
    int maxClientConnections();

    /**
     * Worker pool used to run client command pipelines.
     */
    WorkerThreadConfiguration workerThreads();

    /**
     * Directory where RDB/AOF files and related persistence artifacts are stored.
     */
    String persistenceDirectory();

    /**
     * Server process log verbosity.
     */
    LoggingLevel loggingLevel();

    /**
     * OS listen backlog for the accept socket.
     */
    int tcpBacklog();

    /**
     * Snapshot with all {@link ServerConfigurationDefaults} applied and validated.
     */
    static ServerConfiguration defaults() throws InvalidConfigurationException {
        return new ServerConfigurationBuilder().build();
    }

    /**
     * Starts a fluent builder; unset fields use {@link ServerConfigurationDefaults}.
     */
    static ServerConfigurationBuilder builder() {
        return new ServerConfigurationBuilder();
    }
}
