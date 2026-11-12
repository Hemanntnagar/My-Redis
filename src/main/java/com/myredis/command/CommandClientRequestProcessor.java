package com.myredis.command;

import com.myredis.exception.CommandException;
import com.myredis.network.ClientConnectionManager;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientRequestProcessor;
import com.myredis.network.ClientSession;
import com.myredis.expiration.ExpirationClock;
import com.myredis.expiration.ExpirationService;
import com.myredis.expiration.InMemoryExpirationService;
import com.myredis.expiration.SystemExpirationClock;
import com.myredis.persistence.aof.AppendOnlyFile;
import com.myredis.persistence.aof.WriteCommandClassifier;
import com.myredis.pubsub.InMemoryPubSubBroker;
import com.myredis.pubsub.PubSubBroker;
import com.myredis.storage.ExpirationAwareKeyValueStore;
import com.myredis.storage.InMemoryKeyValueStore;
import com.myredis.storage.KeyValueStore;
import com.myredis.storage.WatchNotifyingKeyValueStore;
import com.myredis.transaction.InMemoryTransactionManager;
import com.myredis.transaction.TransactionManager;
import com.myredis.metrics.ServerStatistics;
import com.myredis.network.ConnectedClientSession;
import com.myredis.replication.ReplicaUpstreamClient;
import com.myredis.replication.ReplicationRole;
import com.myredis.server.ServerCollaborators;
import com.myredis.transaction.WatchRegistry;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Turns decoded RESP command arrays into {@link CommandDispatcher} invocations.
 */
public final class CommandClientRequestProcessor implements ClientRequestProcessor {

    private static final Set<String> TRANSACTION_CONTROL =
            Set.of("MULTI", "EXEC", "DISCARD", "WATCH", "UNWATCH");
    private static final Set<String> PUBSUB_ALLOWED = Set.of("SUBSCRIBE", "UNSUBSCRIBE", "PING");

    private final CommandDispatcher dispatcher;
    private final AppendOnlyFile appendOnlyFile;
    private final TransactionManager transactionManager;
    private final PubSubBroker pubSubBroker;
    private final InMemoryPubSubBroker mutablePubSubBroker;
    private ServerCollaborators collaborators;

    public CommandClientRequestProcessor(CommandDispatcher dispatcher) {
        this(dispatcher, null, null, null);
    }

    public CommandClientRequestProcessor(CommandDispatcher dispatcher, AppendOnlyFile appendOnlyFile) {
        this(dispatcher, appendOnlyFile, null, null);
    }

    private CommandClientRequestProcessor(
            CommandDispatcher dispatcher,
            AppendOnlyFile appendOnlyFile,
            TransactionManager transactionManager,
            PubSubBroker pubSubBroker) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.appendOnlyFile = appendOnlyFile;
        this.transactionManager = transactionManager;
        this.pubSubBroker = pubSubBroker;
        this.mutablePubSubBroker = pubSubBroker instanceof InMemoryPubSubBroker broker ? broker : null;
    }

    public CommandDispatcher dispatcher() {
        return dispatcher;
    }

    public void bindCollaborators(ServerCollaborators collaborators, ClientConnectionManager connections) {
        this.collaborators = Objects.requireNonNull(collaborators, "collaborators");
        collaborators.bindConnections(connections);
    }

    public void bindConnectionManager(ClientConnectionManager connections) {
        if (mutablePubSubBroker != null) {
            mutablePubSubBroker.bindConnectionManager(connections);
        }
    }

    public static CommandClientRequestProcessor withBuiltinCommands() {
        return withBuiltinCommands(new InMemoryKeyValueStore());
    }

    public static CommandClientRequestProcessor withBuiltinCommands(KeyValueStore store) {
        return createBuiltIn(store, null, null, null, null, null, null, null);
    }

    public static CommandClientRequestProcessor withBuiltinCommands(
            KeyValueStore store, ExpirationService expiration, ExpirationClock clock) {
        return createBuiltIn(store, expiration, clock, null, null, null, null, null);
    }

    public static CommandClientRequestProcessor withBuiltinCommands(
            com.myredis.persistence.SnapshotCommandSupport persistence,
            KeyValueStore store,
            ExpirationService expiration,
            ExpirationClock clock) {
        return withBuiltinCommands(persistence, null, store, expiration, clock);
    }

    public static CommandClientRequestProcessor withBuiltinCommands(
            com.myredis.persistence.SnapshotCommandSupport persistence,
            AppendOnlyFile appendOnlyFile,
            KeyValueStore store,
            ExpirationService expiration,
            ExpirationClock clock) {
        return createBuiltIn(store, expiration, clock, persistence, appendOnlyFile, null, null, null);
    }

    public static CommandClientRequestProcessor withBuiltinCommands(
            com.myredis.persistence.SnapshotCommandSupport persistence,
            AppendOnlyFile appendOnlyFile,
            KeyValueStore store,
            ExpirationService expiration,
            ExpirationClock clock,
            WatchRegistry watchRegistry) {
        return createBuiltIn(store, expiration, clock, persistence, appendOnlyFile, watchRegistry, null, null);
    }

    public static CommandClientRequestProcessor withBuiltinCommands(
            com.myredis.persistence.SnapshotCommandSupport persistence,
            AppendOnlyFile appendOnlyFile,
            KeyValueStore store,
            ExpirationService expiration,
            ExpirationClock clock,
            WatchRegistry watchRegistry,
            ServerCollaborators collaborators,
            java.util.function.BiFunction<String, Integer, ReplicaUpstreamClient> upstreamFactory) {
        return createBuiltIn(
                store, expiration, clock, persistence, appendOnlyFile, watchRegistry, collaborators, upstreamFactory);
    }

    public static CommandDispatcher replicationDispatcher(
            KeyValueStore store,
            ExpirationService expiration,
            com.myredis.persistence.SnapshotCommandSupport persistence,
            AppendOnlyFile appendOnlyFile) {
        return createBuiltIn(store, expiration, SystemExpirationClock.INSTANCE, persistence, appendOnlyFile, null, null, null)
                .dispatcher();
    }

    private static CommandClientRequestProcessor createBuiltIn(
            KeyValueStore store,
            ExpirationService expiration,
            ExpirationClock clock,
            com.myredis.persistence.SnapshotCommandSupport persistence,
            AppendOnlyFile appendOnlyFile,
            WatchRegistry providedWatchRegistry,
            ServerCollaborators collaborators,
            java.util.function.BiFunction<String, Integer, ReplicaUpstreamClient> upstreamFactory) {
        WatchRegistry watchRegistry = providedWatchRegistry != null ? providedWatchRegistry : new WatchRegistry();
        KeyValueStore commandStore =
                providedWatchRegistry != null ? store : watchWrappedStore(store, watchRegistry);
        ExpirationService effectiveExpiration = expiration;
        ExpirationClock effectiveClock = clock != null ? clock : SystemExpirationClock.INSTANCE;
        if (commandStore instanceof ExpirationAwareKeyValueStore aware) {
            effectiveExpiration = aware.expirationService();
        } else if (effectiveExpiration == null) {
            effectiveExpiration = new InMemoryExpirationService();
            commandStore =
                    new ExpirationAwareKeyValueStore(commandStore, (InMemoryExpirationService) effectiveExpiration, effectiveClock);
        }
        TransactionManager transactionManager = new InMemoryTransactionManager(watchRegistry);
        InMemoryPubSubBroker pubSubBroker = new InMemoryPubSubBroker(null);
        BuiltinCommands built =
                RegistryCommandDispatcher.assembleBuiltinCommands(
                        commandStore,
                        effectiveExpiration,
                        effectiveClock,
                        persistence,
                        appendOnlyFile,
                        transactionManager,
                        pubSubBroker,
                        collaborators,
                        upstreamFactory);
        return new CommandClientRequestProcessor(
                built.dispatcher(), appendOnlyFile, built.transactionManager(), built.pubSubBroker());
    }

    private static KeyValueStore watchWrappedStore(KeyValueStore store, WatchRegistry watchRegistry) {
        if (store instanceof WatchNotifyingKeyValueStore || store instanceof ExpirationAwareKeyValueStore) {
            return store;
        }
        return new WatchNotifyingKeyValueStore(store, watchRegistry);
    }

    @Override
    public ClientReply process(ClientSession session, List<byte[]> commandParts) {
        if (commandParts.isEmpty()) {
            return ClientReply.error("ERR empty command");
        }
        String commandName = new String(commandParts.getFirst(), StandardCharsets.UTF_8);
        List<byte[]> arguments = commandParts.size() == 1 ? List.of() : commandParts.subList(1, commandParts.size());
        String upper = commandName.toUpperCase(Locale.ROOT);
        try {
            if (collaborators != null
                    && collaborators.replication().role() == ReplicationRole.REPLICA
                    && WriteCommandClassifier.isWriteCommand(upper)
                    && !"REPLICAOF".equals(upper)) {
                return ClientReply.error("READONLY You can't write against a read only replica");
            }
            if (pubSubBroker != null && pubSubBroker.inPubSubMode(session)) {
                if ("PING".equals(upper)) {
                    return pubSubPingReply(session);
                }
                if (!PUBSUB_ALLOWED.contains(upper)) {
                    return ClientReply.error(
                            "ERR only (P)SUBSCRIBE / (P)UNSUBSCRIBE / PING / QUIT allowed in this context");
                }
            }
            if (transactionManager != null && transactionManager.inTransaction(session)) {
                if (TRANSACTION_CONTROL.contains(upper)) {
                    ClientReply reply = dispatcher.dispatch(session, commandName, arguments);
                    appendSuccessfulWrite(commandName, commandParts, reply);
                    return reply;
                }
                transactionManager.enqueue(session, commandName, arguments);
                return new ClientReply.SimpleString("QUEUED");
            }
            ClientReply reply = dispatcher.dispatch(session, commandName, arguments);
            recordCommandMetrics(session, upper);
            appendSuccessfulWrite(commandName, commandParts, reply);
            replicateWrite(commandName, commandParts, reply);
            if (collaborators != null
                    && "REPLSYNC".equals(upper)
                    && collaborators.replication().holdsReplicationLink(session)) {
                return new ClientReply.ReplicationLinkEstablished(reply);
            }
            return reply;
        } catch (CommandException failure) {
            return ClientReply.error(failure.clientMessage());
        }
    }

    @Override
    public void onDisconnect(ClientSession session) {
        if (transactionManager != null) {
            transactionManager.clientDisconnected(session);
        }
        if (pubSubBroker != null) {
            pubSubBroker.clientDisconnected(session);
        }
        if (collaborators != null && session instanceof ConnectedClientSession connected) {
            collaborators.replication().unregisterReplica(connected);
        }
    }

    private ClientReply pubSubPingReply(ClientSession session) {
        return new ClientReply.CompositeArray(
                List.of(
                        new ClientReply.BulkString("pong".getBytes(StandardCharsets.UTF_8)),
                        new ClientReply.Integer(pubSubBroker.subscriptionCount(session))));
    }

    private void recordCommandMetrics(ClientSession session, String upperCommand) {
        if (collaborators == null) {
            return;
        }
        collaborators.metrics().increment(ServerStatistics.METRIC_COMMANDS_PROCESSED);
        collaborators.metrics().increment("cmdstat_" + upperCommand.toLowerCase(Locale.ROOT));
        if (session instanceof ConnectedClientSession connected) {
            connected.recordCommandProcessed();
        }
    }

    private void replicateWrite(String commandName, List<byte[]> commandParts, ClientReply reply) {
        if (collaborators == null || reply instanceof ClientReply.Error) {
            return;
        }
        if (collaborators.replication().role() != ReplicationRole.MASTER) {
            return;
        }
        if (!WriteCommandClassifier.isWriteCommand(commandName)) {
            return;
        }
        collaborators.replication().propagateWrite(commandParts);
    }

    private void appendSuccessfulWrite(String commandName, List<byte[]> commandParts, ClientReply reply) {
        if (appendOnlyFile == null || reply instanceof ClientReply.Error) {
            return;
        }
        if (!WriteCommandClassifier.isWriteCommand(commandName)) {
            return;
        }
        try {
            appendOnlyFile.append(commandParts);
        } catch (com.myredis.exception.PersistenceException failure) {
            System.err.println(
                    "AOF append failed for "
                            + commandName.toUpperCase(Locale.ROOT)
                            + ": "
                            + failure.clientMessage());
        }
    }
}
