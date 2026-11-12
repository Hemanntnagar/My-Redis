package com.myredis.config;

import com.myredis.exception.InvalidConfigurationException;

/**
 * Fluent builder for validated {@link ServerConfiguration} instances.
 */
public final class ServerConfigurationBuilder {

    private String host = ServerConfigurationDefaults.HOST;
    private int port = ServerConfigurationDefaults.PORT;
    private int maxClientConnections = ServerConfigurationDefaults.MAX_CLIENT_CONNECTIONS;
    private WorkerThreadConfiguration workerThreads =
            new WorkerThreadConfiguration(ServerConfigurationDefaults.defaultWorkerThreadCount());
    private String persistenceDirectory = ServerConfigurationDefaults.defaultPersistenceDirectory();
    private LoggingLevel loggingLevel = ServerConfigurationDefaults.LOGGING_LEVEL;
    private int tcpBacklog = ServerConfigurationDefaults.TCP_BACKLOG;

    public ServerConfigurationBuilder host(String host) {
        this.host = host;
        return this;
    }

    public ServerConfigurationBuilder port(int port) {
        this.port = port;
        return this;
    }

    public ServerConfigurationBuilder maxClientConnections(int maxClientConnections) {
        this.maxClientConnections = maxClientConnections;
        return this;
    }

    public ServerConfigurationBuilder workerThreads(int threadCount) {
        this.workerThreads = new WorkerThreadConfiguration(threadCount);
        return this;
    }

    public ServerConfigurationBuilder workerThreads(WorkerThreadConfiguration workerThreads) {
        this.workerThreads = workerThreads;
        return this;
    }

    public ServerConfigurationBuilder persistenceDirectory(String persistenceDirectory) {
        this.persistenceDirectory = persistenceDirectory;
        return this;
    }

    public ServerConfigurationBuilder loggingLevel(LoggingLevel loggingLevel) {
        this.loggingLevel = loggingLevel;
        return this;
    }

    public ServerConfigurationBuilder tcpBacklog(int tcpBacklog) {
        this.tcpBacklog = tcpBacklog;
        return this;
    }

    public ServerConfiguration build() throws InvalidConfigurationException {
        ServerConfiguration configuration =
                new DefaultServerConfiguration(
                        host,
                        port,
                        maxClientConnections,
                        workerThreads,
                        persistenceDirectory,
                        loggingLevel,
                        tcpBacklog);
        ServerConfigurationValidator.validate(configuration);
        return configuration;
    }
}
