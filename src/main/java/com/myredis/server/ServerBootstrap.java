package com.myredis.server;

import com.myredis.config.ServerConfiguration;
import com.myredis.exception.ServerErrorHandler;
import com.myredis.logging.StructuredServerLogger;
import com.myredis.command.CommandClientRequestProcessor;
import com.myredis.metrics.MetricsRecorder;
import com.myredis.metrics.NoOpMetricsRecorder;
import com.myredis.persistence.PersistenceManager;
import com.myredis.persistence.SnapshotPersistenceManager;
import com.myredis.expiration.BackgroundExpirationCleanup;
import com.myredis.expiration.ExpirationCleanupPolicy;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.expiration.SystemExpirationClock;
import com.myredis.storage.ExpirationAwareKeyValueStore;
import com.myredis.storage.InMemoryKeyValueStore;
import com.myredis.storage.KeyValueStore;

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
        InMemoryExpirationService expiration = new InMemoryExpirationService();
        ExpirationAwareKeyValueStore keyValueStore =
                new ExpirationAwareKeyValueStore(
                        new InMemoryKeyValueStore(), expiration, SystemExpirationClock.INSTANCE);
        PersistenceManager persistence =
                new SnapshotPersistenceManager(configuration.persistenceDirectory(), keyValueStore, expiration);
        BackgroundExpirationCleanup expirationCleanup =
                new BackgroundExpirationCleanup(keyValueStore, expiration, ExpirationCleanupPolicy.defaults());
        DatabaseServer databaseServer =
                new LifecycleDatabaseServer(
                        configuration,
                        logger,
                        CommandClientRequestProcessor.withBuiltinCommands(
                                (SnapshotPersistenceManager) persistence,
                                keyValueStore,
                                expiration,
                                SystemExpirationClock.INSTANCE));

        return new ServerContext(
                configuration, logger, errorHandler, metrics, persistence, databaseServer, expirationCleanup);
    }
}
