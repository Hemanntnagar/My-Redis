package com.myredis.server;

import com.myredis.config.ServerConfiguration;
import com.myredis.exception.PersistenceException;
import com.myredis.exception.ServerErrorHandler;
import com.myredis.logging.StructuredServerLogger;
import com.myredis.command.CommandClientRequestProcessor;
import com.myredis.metrics.MetricsRecorder;
import com.myredis.metrics.NoOpMetricsRecorder;
import com.myredis.persistence.CompositePersistenceManager;
import com.myredis.persistence.PersistenceManager;
import com.myredis.persistence.SnapshotPersistenceManager;
import com.myredis.persistence.aof.AppendOnlyFile;
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
        com.myredis.transaction.WatchRegistry watchRegistry = new com.myredis.transaction.WatchRegistry();
        ExpirationAwareKeyValueStore keyValueStore =
                new ExpirationAwareKeyValueStore(
                        new com.myredis.storage.WatchNotifyingKeyValueStore(
                                new InMemoryKeyValueStore(), watchRegistry),
                        expiration,
                        SystemExpirationClock.INSTANCE);
        SnapshotPersistenceManager snapshot =
                new SnapshotPersistenceManager(configuration.persistenceDirectory(), keyValueStore, expiration);
        AppendOnlyFile appendOnlyFile = null;
        if (configuration.aofEnabled()) {
            try {
                appendOnlyFile =
                        AppendOnlyFile.open(
                                java.nio.file.Path.of(configuration.persistenceDirectory()),
                                configuration.aofFsyncPolicy());
            } catch (PersistenceException failure) {
                throw new IllegalStateException("failed to open AOF: " + failure.clientMessage(), failure);
            }
        }
        PersistenceManager persistence =
                new CompositePersistenceManager(
                        snapshot,
                        appendOnlyFile,
                        keyValueStore,
                        expiration,
                        SystemExpirationClock.INSTANCE);
        BackgroundExpirationCleanup expirationCleanup =
                new BackgroundExpirationCleanup(keyValueStore, expiration, ExpirationCleanupPolicy.defaults());
        CompositePersistenceManager compositePersistence = (CompositePersistenceManager) persistence;
        CommandClientRequestProcessor requestProcessor =
                CommandClientRequestProcessor.withBuiltinCommands(
                        compositePersistence,
                        appendOnlyFile,
                        keyValueStore,
                        expiration,
                        SystemExpirationClock.INSTANCE,
                        watchRegistry);
        DatabaseServer databaseServer =
                new LifecycleDatabaseServer(configuration, logger, requestProcessor);

        return new ServerContext(
                configuration, logger, errorHandler, metrics, persistence, databaseServer, expirationCleanup);
    }
}
