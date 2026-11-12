package com.myredis.metrics;

import com.myredis.config.MaxMemoryPolicy;
import com.myredis.config.ServerConfiguration;
import com.myredis.network.ClientConnectionManager;
import com.myredis.replication.ReplicationCoordinator;
import com.myredis.storage.MemoryUsageTracker;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregates runtime figures for {@code INFO} and operators.
 */
public final class ServerStatistics {

    public static final String METRIC_COMMANDS_PROCESSED = "commands_processed_total";
    public static final String METRIC_CONNECTIONS_RECEIVED = "connections_received_total";
    public static final String METRIC_EVICTED_KEYS = "evicted_keys_total";

    private final Instant startedAt;
    private final AtomicMetricsRecorder metrics;
    private final ServerConfiguration configuration;
    private final ClientConnectionManager connections;
    private final ReplicationCoordinator replication;
    private final MemoryUsageTracker memory;

    public ServerStatistics(
            Instant startedAt,
            AtomicMetricsRecorder metrics,
            ServerConfiguration configuration,
            ClientConnectionManager connections,
            ReplicationCoordinator replication,
            MemoryUsageTracker memory) {
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.connections = connections;
        this.replication = Objects.requireNonNull(replication, "replication");
        this.memory = Objects.requireNonNull(memory, "memory");
    }

    public long uptimeSeconds() {
        return Math.max(0, Instant.now().getEpochSecond() - startedAt.getEpochSecond());
    }

    public long commandsProcessed() {
        return metrics.getCounter(METRIC_COMMANDS_PROCESSED);
    }

    public long connectionsReceived() {
        return metrics.getCounter(METRIC_CONNECTIONS_RECEIVED);
    }

    public long evictedKeys() {
        return metrics.getCounter(METRIC_EVICTED_KEYS);
    }

    public int connectedClients() {
        return connections == null ? 0 : connections.activeCount();
    }

    public long usedMemoryBytes() {
        return memory.usedBytes();
    }

    public String renderInfoSection(String section) {
        String normalized = section == null ? "default" : section.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "", "default", "all" -> renderAll();
            case "server" -> renderServer();
            case "clients" -> renderClients();
            case "memory" -> renderMemory();
            case "stats" -> renderStats();
            case "replication" -> renderReplication();
            default -> "# Unknown section";
        };
    }

    private String renderAll() {
        return renderServer()
                + "\r\n"
                + renderClients()
                + "\r\n"
                + renderMemory()
                + "\r\n"
                + renderStats()
                + "\r\n"
                + renderReplication();
    }

    private String renderServer() {
        return "# Server\r\n"
                + "redis_mode:standalone\r\n"
                + "myredis_version:0.1.0\r\n"
                + "uptime_in_seconds:"
                + uptimeSeconds()
                + "\r\n"
                + "tcp_port:"
                + configuration.port()
                + "\r\n";
    }

    private String renderClients() {
        return "# Clients\r\n"
                + "connected_clients:"
                + connectedClients()
                + "\r\n"
                + "maxclients:"
                + configuration.maxClientConnections()
                + "\r\n";
    }

    private String renderMemory() {
        MaxMemoryPolicy policy = configuration.maxMemoryPolicy();
        long limit = configuration.maxMemoryBytes();
        return "# Memory\r\n"
                + "used_memory:"
                + usedMemoryBytes()
                + "\r\n"
                + "used_memory_human:"
                + humanBytes(usedMemoryBytes())
                + "\r\n"
                + "maxmemory:"
                + limit
                + "\r\n"
                + "maxmemory_policy:"
                + policy.name().toLowerCase(Locale.ROOT).replace('_', '-')
                + "\r\n"
                + "evicted_keys:"
                + evictedKeys()
                + "\r\n";
    }

    private String renderStats() {
        StringBuilder builder = new StringBuilder("# Stats\r\n");
        builder.append("total_connections_received:").append(connectionsReceived()).append("\r\n");
        builder.append("total_commands_processed:").append(commandsProcessed()).append("\r\n");
        for (Map.Entry<String, Long> entry : metrics.snapshotCounters().entrySet()) {
            if (entry.getKey().startsWith("cmdstat_")) {
                builder.append(entry.getKey()).append(":").append(entry.getValue()).append("\r\n");
            }
        }
        return builder.toString();
    }

    private String renderReplication() {
        return "# Replication\r\n"
                + "role:"
                + (replication.role().name().equals("MASTER") ? "master" : "slave")
                + "\r\n"
                + "connected_slaves:"
                + replication.connectedReplicaCount()
                + "\r\n"
                + "master_repl_offset:"
                + replication.replicationOffset()
                + "\r\n";
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.2fK", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.ROOT, "%.2fM", mb);
        }
        return String.format(Locale.ROOT, "%.2fG", mb / 1024.0);
    }
}
