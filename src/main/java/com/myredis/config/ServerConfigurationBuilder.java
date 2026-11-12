package com.myredis.config;

import java.time.Duration;

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
    private Duration clientIdleTimeout = ServerConfigurationDefaults.CLIENT_IDLE_TIMEOUT;
    private boolean aofEnabled = ServerConfigurationDefaults.AOF_ENABLED;
    private com.myredis.persistence.aof.AofFsyncPolicy aofFsyncPolicy =
            ServerConfigurationDefaults.AOF_FSYNC_POLICY;
    private long maxMemoryBytes = ServerConfigurationDefaults.MAX_MEMORY_BYTES;
    private MaxMemoryPolicy maxMemoryPolicy = ServerConfigurationDefaults.MAX_MEMORY_POLICY;
    private String replicaMasterHost = ServerConfigurationDefaults.REPLICA_MASTER_HOST;
    private int replicaMasterPort = ServerConfigurationDefaults.REPLICA_MASTER_PORT;
    private int replicationBacklogSize = ServerConfigurationDefaults.REPLICATION_BACKLOG_SIZE;

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

    public ServerConfigurationBuilder clientIdleTimeout(Duration clientIdleTimeout) {
        this.clientIdleTimeout = clientIdleTimeout;
        return this;
    }

    public ServerConfigurationBuilder clientIdleTimeoutMillis(long millis) {
        this.clientIdleTimeout = Duration.ofMillis(millis);
        return this;
    }

    public ServerConfigurationBuilder aofEnabled(boolean aofEnabled) {
        this.aofEnabled = aofEnabled;
        return this;
    }

    public ServerConfigurationBuilder aofFsyncPolicy(com.myredis.persistence.aof.AofFsyncPolicy aofFsyncPolicy) {
        this.aofFsyncPolicy = aofFsyncPolicy;
        return this;
    }

    public ServerConfigurationBuilder maxMemoryBytes(long maxMemoryBytes) {
        this.maxMemoryBytes = maxMemoryBytes;
        return this;
    }

    public ServerConfigurationBuilder maxMemoryPolicy(MaxMemoryPolicy maxMemoryPolicy) {
        this.maxMemoryPolicy = maxMemoryPolicy;
        return this;
    }

    public ServerConfigurationBuilder replicaOf(String masterHost, int masterPort) {
        this.replicaMasterHost = masterHost;
        this.replicaMasterPort = masterPort;
        return this;
    }

    public ServerConfigurationBuilder replicationBacklogSize(int replicationBacklogSize) {
        this.replicationBacklogSize = replicationBacklogSize;
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
                        tcpBacklog,
                        clientIdleTimeout,
                        aofEnabled,
                        aofFsyncPolicy,
                        maxMemoryBytes,
                        maxMemoryPolicy,
                        replicaMasterHost,
                        replicaMasterPort,
                        replicationBacklogSize);
        ServerConfigurationValidator.validate(configuration);
        return configuration;
    }
}
