package com.myredis.server;

import com.myredis.command.CommandDispatcher;
import com.myredis.config.ServerConfiguration;
import com.myredis.metrics.AtomicMetricsRecorder;
import com.myredis.metrics.ServerStatistics;
import com.myredis.network.ClientConnectionManager;
import com.myredis.replication.InMemoryReplicationCoordinator;
import com.myredis.replication.ReplicaUpstreamClient;
import com.myredis.storage.MemoryUsageTracker;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cross-cutting collaborators assembled at bootstrap and shared with commands.
 */
public final class ServerCollaborators {

    private final Instant startedAt;
    private final AtomicMetricsRecorder metrics;
    private final MemoryUsageTracker memory;
    private final InMemoryReplicationCoordinator replication;
    private final AtomicReference<ClientConnectionManager> connections = new AtomicReference<>();
    private final AtomicReference<ReplicaUpstreamClient> upstreamClient = new AtomicReference<>();
    private volatile CommandDispatcher replicationApplyDispatcher;
    private final ServerConfiguration configuration;

    public ServerCollaborators(
            ServerConfiguration configuration,
            AtomicMetricsRecorder metrics,
            MemoryUsageTracker memory,
            InMemoryReplicationCoordinator replication) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.startedAt = Instant.now();
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.memory = Objects.requireNonNull(memory, "memory");
        this.replication = Objects.requireNonNull(replication, "replication");
    }

    public Instant startedAt() {
        return startedAt;
    }

    public AtomicMetricsRecorder metrics() {
        return metrics;
    }

    public MemoryUsageTracker memory() {
        return memory;
    }

    public InMemoryReplicationCoordinator replication() {
        return replication;
    }

    public ServerConfiguration configuration() {
        return configuration;
    }

    public void bindConnections(ClientConnectionManager connections) {
        this.connections.set(Objects.requireNonNull(connections, "connections"));
    }

    public void onClientConnected() {
        metrics.increment(ServerStatistics.METRIC_CONNECTIONS_RECEIVED);
    }

    public ClientConnectionManager connections() {
        return connections.get();
    }

    public ServerStatistics statistics() {
        return new ServerStatistics(
                startedAt, metrics, configuration, connections.get(), replication, memory);
    }

    public void bindReplicationApplyDispatcher(CommandDispatcher dispatcher) {
        this.replicationApplyDispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    public CommandDispatcher replicationApplyDispatcher() {
        return replicationApplyDispatcher;
    }

    public void startReplicaUpstreamIfConfigured(ReplicaUpstreamClient client) {
        upstreamClient.set(client);
        if (configuration.replicaMasterHost() != null && configuration.replicaMasterPort() > 0) {
            replication.configureAsReplica(configuration.replicaMasterHost(), configuration.replicaMasterPort());
            client.start();
        }
    }

    public void close() {
        ReplicaUpstreamClient client = upstreamClient.get();
        if (client != null) {
            client.close();
        }
    }
}
