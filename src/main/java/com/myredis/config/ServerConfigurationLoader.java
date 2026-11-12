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

        return builder.build();
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

    private static void applyInt(java.util.function.IntConsumer setter, Optional<String> value)
            throws InvalidConfigurationException {
        if (value.isPresent()) {
            setter.accept(parseInt(value.get()));
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
}
