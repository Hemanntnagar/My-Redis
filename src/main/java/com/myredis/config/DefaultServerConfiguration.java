package com.myredis.config;

import java.time.Duration;

import com.myredis.exception.InvalidConfigurationException;

/**
 * Immutable {@link ServerConfiguration} produced only through {@link ServerConfigurationBuilder}.
 */
final class DefaultServerConfiguration implements ServerConfiguration {

    private final String host;
    private final int port;
    private final int maxClientConnections;
    private final WorkerThreadConfiguration workerThreads;
    private final String persistenceDirectory;
    private final LoggingLevel loggingLevel;
    private final int tcpBacklog;
    private final Duration clientIdleTimeout;
    private final boolean aofEnabled;
    private final com.myredis.persistence.aof.AofFsyncPolicy aofFsyncPolicy;

    DefaultServerConfiguration(
            String host,
            int port,
            int maxClientConnections,
            WorkerThreadConfiguration workerThreads,
            String persistenceDirectory,
            LoggingLevel loggingLevel,
            int tcpBacklog,
            Duration clientIdleTimeout,
            boolean aofEnabled,
            com.myredis.persistence.aof.AofFsyncPolicy aofFsyncPolicy) {
        this.host = host;
        this.port = port;
        this.maxClientConnections = maxClientConnections;
        this.workerThreads = workerThreads;
        this.persistenceDirectory = persistenceDirectory;
        this.loggingLevel = loggingLevel;
        this.tcpBacklog = tcpBacklog;
        this.clientIdleTimeout = clientIdleTimeout;
        this.aofEnabled = aofEnabled;
        this.aofFsyncPolicy = aofFsyncPolicy;
    }

    @Override
    public String host() {
        return host;
    }

    @Override
    public int port() {
        return port;
    }

    @Override
    public int maxClientConnections() {
        return maxClientConnections;
    }

    @Override
    public WorkerThreadConfiguration workerThreads() {
        return workerThreads;
    }

    @Override
    public String persistenceDirectory() {
        return persistenceDirectory;
    }

    @Override
    public LoggingLevel loggingLevel() {
        return loggingLevel;
    }

    @Override
    public int tcpBacklog() {
        return tcpBacklog;
    }

    @Override
    public Duration clientIdleTimeout() {
        return clientIdleTimeout;
    }

    @Override
    public boolean aofEnabled() {
        return aofEnabled;
    }

    @Override
    public com.myredis.persistence.aof.AofFsyncPolicy aofFsyncPolicy() {
        return aofFsyncPolicy;
    }
}
