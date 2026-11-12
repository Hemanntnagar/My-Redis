package com.myredis.server;

import com.myredis.config.ServerConfiguration;
import com.myredis.exception.ServerErrorHandler;
import com.myredis.logging.StructuredServerLogger;
import com.myredis.metrics.MetricsRecorder;
import com.myredis.metrics.NoOpMetricsRecorder;
import com.myredis.persistence.NoOpPersistenceManager;
import com.myredis.persistence.PersistenceManager;

import java.util.Objects;

/**
 * Assembles cross-cutting collaborators from configuration without binding command or storage logic.
 */
public final class ServerBootstrap {

    public ServerContext bootstrap(ServerConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");

        StructuredServerLogger logger = new StructuredServerLogger();
        logger.applyLoggingLevel(configuration.loggingLevel());

        ServerErrorHandler errorHandler = new ServerErrorHandler(logger);
        MetricsRecorder metrics = new NoOpMetricsRecorder();
        PersistenceManager persistence = new NoOpPersistenceManager();
        DatabaseServer databaseServer = new LifecycleDatabaseServer(configuration, logger);

        return new ServerContext(configuration, logger, errorHandler, metrics, persistence, databaseServer);
    }
}
