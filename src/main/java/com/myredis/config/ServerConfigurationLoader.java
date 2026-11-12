package com.myredis.config;

import com.myredis.exception.InvalidConfigurationException;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Loads {@link ServerConfiguration} from the process environment without coupling callers to a
 * specific source format.
 */
public final class ServerConfigurationLoader {

    private ServerConfigurationLoader() {}

    /**
     * Resolves settings from {@code myredis.*} system properties, then {@code MYREDIS_*} environment
     * variables, then {@link ServerConfigurationDefaults}.
     */
    public static ServerConfiguration load() throws InvalidConfigurationException {
        ServerConfigurationBuilder builder = ServerConfiguration.builder();

        applyString(builder::host, resolveString("myredis.host", "MYREDIS_HOST"));
        applyInt(builder::port, resolveString("myredis.port", "MYREDIS_PORT"));
        applyInt(
                builder::maxClientConnections,
                resolveString("myredis.maxClientConnections", "MYREDIS_MAX_CLIENT_CONNECTIONS"));
        applyInt(builder::workerThreads, resolveString("myredis.workerThreads", "MYREDIS_WORKER_THREADS"));
        applyString(
                builder::persistenceDirectory,
                resolveString("myredis.persistenceDirectory", "MYREDIS_PERSISTENCE_DIRECTORY"));
        applyLoggingLevel(
                builder::loggingLevel, resolveString("myredis.loggingLevel", "MYREDIS_LOGGING_LEVEL"));
        applyInt(builder::tcpBacklog, resolveString("myredis.tcpBacklog", "MYREDIS_TCP_BACKLOG"));
        applyDurationMillis(
                builder::clientIdleTimeout,
                resolveString("myredis.clientIdleTimeoutMillis", "MYREDIS_CLIENT_IDLE_TIMEOUT_MS"));
        applyBoolean(builder::aofEnabled, resolveString("myredis.aofEnabled", "MYREDIS_AOF_ENABLED"));
        applyAofFsyncPolicy(
                builder::aofFsyncPolicy, resolveString("myredis.aofFsyncPolicy", "MYREDIS_AOF_FSYNC_POLICY"));
        applyLong(builder::maxMemoryBytes, resolveString("myredis.maxMemoryBytes", "MYREDIS_MAX_MEMORY_BYTES"));
        applyMaxMemoryPolicy(
                builder::maxMemoryPolicy, resolveString("myredis.maxMemoryPolicy", "MYREDIS_MAX_MEMORY_POLICY"));
        Optional<String> replicaHost =
                resolveString("myredis.replicaMasterHost", "MYREDIS_REPLICA_MASTER_HOST");
        Optional<String> replicaPort =
                resolveString("myredis.replicaMasterPort", "MYREDIS_REPLICA_MASTER_PORT");
        applyReplicaOf(builder, replicaHost, replicaPort);
        applyInt(
                builder::replicationBacklogSize,
                resolveString("myredis.replicationBacklogSize", "MYREDIS_REPLICATION_BACKLOG_SIZE"));

        return builder.build();
    }

    private static void applyReplicaOf(
            ServerConfigurationBuilder builder, Optional<String> host, Optional<String> port)
            throws InvalidConfigurationException {
        if (host.isEmpty() && port.isEmpty()) {
            return;
        }
        if (host.isPresent() && port.isPresent()) {
            builder.replicaOf(host.get(), parseInt(port.get()));
            return;
        }
        throw new InvalidConfigurationException("replicaMasterHost and replicaMasterPort must be set together");
    }

    private static Optional<String> resolveString(String propertyKey, String environmentKey) {
        String value = System.getProperty(propertyKey);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentKey);
        }
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }

    private static void applyString(java.util.function.Consumer<String> setter, Optional<String> value) {
        value.ifPresent(setter);
    }

    private static void applyBoolean(java.util.function.Consumer<Boolean> setter, Optional<String> value)
            throws InvalidConfigurationException {
        if (value.isPresent()) {
            setter.accept(parseBoolean(value.get()));
        }
    }

    private static void applyAofFsyncPolicy(
            java.util.function.Consumer<com.myredis.persistence.aof.AofFsyncPolicy> setter, Optional<String> value)
            throws InvalidConfigurationException {
        if (value.isPresent()) {
            setter.accept(parseAofFsyncPolicy(value.get()));
        }
    }

    private static void applyInt(java.util.function.IntConsumer setter, Optional<String> value)
            throws InvalidConfigurationException {
        if (value.isPresent()) {
            setter.accept(parseInt(value.get()));
        }
    }

    private static void applyLong(java.util.function.LongConsumer setter, Optional<String> value)
            throws InvalidConfigurationException {
        if (value.isPresent()) {
            setter.accept(parseLong(value.get()));
        }
    }

    private static void applyMaxMemoryPolicy(
            java.util.function.Consumer<MaxMemoryPolicy> setter, Optional<String> value)
            throws InvalidConfigurationException {
        if (value.isPresent()) {
            setter.accept(parseMaxMemoryPolicy(value.get()));
        }
    }

    private static void applyLoggingLevel(
            java.util.function.Consumer<LoggingLevel> setter, Optional<String> value)
            throws InvalidConfigurationException {
        if (value.isPresent()) {
            setter.accept(parseLoggingLevel(value.get()));
        }
    }

    private static void applyDurationMillis(
            java.util.function.Consumer<Duration> setter, Optional<String> value)
            throws InvalidConfigurationException {
        if (value.isPresent()) {
            setter.accept(Duration.ofMillis(parseLong(value.get())));
        }
    }

    private static int parseInt(String raw) throws InvalidConfigurationException {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new InvalidConfigurationException(
                    "expected integer for configuration value \"" + raw + "\"", e);
        }
    }

    private static long parseLong(String raw) throws InvalidConfigurationException {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new InvalidConfigurationException(
                    "expected long for configuration value \"" + raw + "\"", e);
        }
    }

    private static LoggingLevel parseLoggingLevel(String raw) throws InvalidConfigurationException {
        try {
            return LoggingLevel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidConfigurationException("unknown logging level \"" + raw + "\"", e);
        }
    }

    private static boolean parseBoolean(String raw) throws InvalidConfigurationException {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> throw new InvalidConfigurationException("expected boolean for configuration value \"" + raw + "\"");
        };
    }

    private static com.myredis.persistence.aof.AofFsyncPolicy parseAofFsyncPolicy(String raw)
            throws InvalidConfigurationException {
        try {
            return com.myredis.persistence.aof.AofFsyncPolicy.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidConfigurationException("unknown AOF fsync policy \"" + raw + "\"", e);
        }
    }

    private static MaxMemoryPolicy parseMaxMemoryPolicy(String raw) throws InvalidConfigurationException {
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return MaxMemoryPolicy.valueOf(normalized);
        } catch (IllegalArgumentException failure) {
            throw new InvalidConfigurationException("unknown maxmemory policy \"" + raw + "\"", failure);
        }
    }
}
