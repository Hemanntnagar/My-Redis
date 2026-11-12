package com.myredis.server;

import com.myredis.config.ServerConfiguration;
import com.myredis.exception.ServerErrorHandler;
import com.myredis.logging.StructuredServerLogger;
import com.myredis.metrics.MetricsRecorder;
import com.myredis.persistence.PersistenceManager;
import com.myredis.expiration.BackgroundExpirationCleanup;

import java.util.Objects;

/**
 * Read-only handle to bootstrap collaborators; keeps the entry point decoupled from concrete
 * implementations.
 */
public final class ServerContext {

    private final ServerConfiguration configuration;
    private final StructuredServerLogger logger;
    private final ServerErrorHandler errorHandler;
    private final MetricsRecorder metrics;
    private final PersistenceManager persistence;
    private final DatabaseServer databaseServer;
    private final BackgroundExpirationCleanup expirationCleanup;
    private final ServerCollaborators collaborators;

    public ServerContext(
            ServerConfiguration configuration,
            StructuredServerLogger logger,
            ServerErrorHandler errorHandler,
            MetricsRecorder metrics,
            PersistenceManager persistence,
            DatabaseServer databaseServer,
            BackgroundExpirationCleanup expirationCleanup) {
        this(
                configuration,
                logger,
                errorHandler,
                metrics,
                persistence,
                databaseServer,
                expirationCleanup,
                null);
    }

    public ServerContext(
            ServerConfiguration configuration,
            StructuredServerLogger logger,
            ServerErrorHandler errorHandler,
            MetricsRecorder metrics,
            PersistenceManager persistence,
            DatabaseServer databaseServer,
            BackgroundExpirationCleanup expirationCleanup,
            ServerCollaborators collaborators) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.databaseServer = Objects.requireNonNull(databaseServer, "databaseServer");
        this.expirationCleanup = Objects.requireNonNull(expirationCleanup, "expirationCleanup");
        this.collaborators = collaborators;
    }

    public ServerConfiguration configuration() {
        return configuration;
    }

    public StructuredServerLogger logger() {
        return logger;
    }

    public ServerErrorHandler errorHandler() {
        return errorHandler;
    }

    public MetricsRecorder metrics() {
        return metrics;
    }

    public PersistenceManager persistence() {
        return persistence;
    }

    public DatabaseServer databaseServer() {
        return databaseServer;
    }

    public BackgroundExpirationCleanup expirationCleanup() {
        return expirationCleanup;
    }

    public ServerCollaborators collaborators() {
        return collaborators;
    }
}
