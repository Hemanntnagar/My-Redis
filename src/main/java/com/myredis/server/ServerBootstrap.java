package com.myredis.server;

import com.myredis.config.ServerConfiguration;
import com.myredis.exception.PersistenceException;
import com.myredis.exception.ServerErrorHandler;
import com.myredis.logging.StructuredServerLogger;
import com.myredis.command.CommandClientRequestProcessor;
import com.myredis.metrics.AtomicMetricsRecorder;
import com.myredis.persistence.CompositePersistenceManager;
import com.myredis.persistence.PersistenceManager;
import com.myredis.persistence.SnapshotPersistenceManager;
import com.myredis.persistence.aof.AppendOnlyFile;
import com.myredis.expiration.BackgroundExpirationCleanup;
import com.myredis.expiration.ExpirationCleanupPolicy;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.expiration.SystemExpirationClock;
import com.myredis.replication.InMemoryReplicationCoordinator;
import com.myredis.replication.ReplicaUpstreamClient;
import com.myredis.storage.EvictingKeyValueStore;
import com.myredis.storage.ExpirationAwareKeyValueStore;
import com.myredis.storage.InMemoryKeyValueStore;
import com.myredis.storage.KeyValueStore;
import com.myredis.storage.MemoryUsageTracker;
import com.myredis.storage.WatchNotifyingKeyValueStore;

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
        AtomicMetricsRecorder metrics = new AtomicMetricsRecorder();
        MemoryUsageTracker memory = new MemoryUsageTracker();
        InMemoryExpirationService expiration = new InMemoryExpirationService();
        com.myredis.transaction.WatchRegistry watchRegistry = new com.myredis.transaction.WatchRegistry();

        KeyValueStore baseStore = new InMemoryKeyValueStore();
        if (configuration.maxMemoryBytes() > 0 || configuration.maxMemoryPolicy() != com.myredis.config.MaxMemoryPolicy.NOEVICTION) {
            baseStore =
                    new EvictingKeyValueStore(
                            baseStore,
                            memory,
                            configuration.maxMemoryBytes(),
                            configuration.maxMemoryPolicy(),
                            expiration,
                            metrics);
        }

        ExpirationAwareKeyValueStore keyValueStore =
                new ExpirationAwareKeyValueStore(
                        new WatchNotifyingKeyValueStore(baseStore, watchRegistry), expiration, SystemExpirationClock.INSTANCE);
        SnapshotPersistenceManager snapshot =
                new SnapshotPersistenceManager(configuration.persistenceDirectory(), keyValueStore, expiration);
        InMemoryReplicationCoordinator replication =
                new InMemoryReplicationCoordinator(
                        keyValueStore, snapshot, configuration.replicationBacklogSize());
        ServerCollaborators collaborators = new ServerCollaborators(configuration, metrics, memory, replication);

        final AppendOnlyFile appendOnlyFile;
        if (configuration.aofEnabled()) {
            try {
                appendOnlyFile =
                        AppendOnlyFile.open(
                                java.nio.file.Path.of(configuration.persistenceDirectory()),
                                configuration.aofFsyncPolicy());
            } catch (PersistenceException failure) {
                throw new IllegalStateException("failed to open AOF: " + failure.clientMessage(), failure);
            }
        } else {
            appendOnlyFile = null;
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

        var replicationApplyDispatcher =
                CommandClientRequestProcessor.replicationDispatcher(
                        keyValueStore, expiration, snapshot, appendOnlyFile);
        collaborators.bindReplicationApplyDispatcher(replicationApplyDispatcher);

        CommandClientRequestProcessor requestProcessor =
                CommandClientRequestProcessor.withBuiltinCommands(
                        compositePersistence,
                        appendOnlyFile,
                        keyValueStore,
                        expiration,
                        SystemExpirationClock.INSTANCE,
                        watchRegistry,
                        collaborators,
                        (host, port) ->
                                new ReplicaUpstreamClient(host, port, replicationApplyDispatcher, replication));

        DatabaseServer databaseServer =
                new LifecycleDatabaseServer(configuration, logger, requestProcessor, collaborators);

        if (configuration.replicaMasterHost() != null && configuration.replicaMasterPort() > 0) {
            ReplicaUpstreamClient upstream =
                    new ReplicaUpstreamClient(
                            configuration.replicaMasterHost(),
                            configuration.replicaMasterPort(),
                            replicationApplyDispatcher,
                            replication);
            collaborators.startReplicaUpstreamIfConfigured(upstream);
        }

        return new ServerContext(
                configuration,
                logger,
                errorHandler,
                metrics,
                persistence,
                databaseServer,
                expirationCleanup,
                collaborators);
    }
}
