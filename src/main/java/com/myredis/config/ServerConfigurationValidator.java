package com.myredis.config;

import com.myredis.exception.InvalidConfigurationException;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Validates configuration snapshots; kept separate from command and storage logic.
 */
public final class ServerConfigurationValidator {

    private ServerConfigurationValidator() {}

    public static void validate(ServerConfiguration configuration) throws InvalidConfigurationException {
        validateHost(configuration.host());
        validatePort(configuration.port());
        validateMaxClientConnections(configuration.maxClientConnections());
        validateWorkerThreads(configuration.workerThreads());
        validatePersistenceDirectory(configuration.persistenceDirectory());
        validateLoggingLevel(configuration.loggingLevel());
        validateTcpBacklog(configuration.tcpBacklog());
    }

    static void validateHost(String host) throws InvalidConfigurationException {
        if (host == null || host.isBlank()) {
            throw new InvalidConfigurationException("host must be a non-blank string");
        }
        if (host.indexOf('\0') >= 0) {
            throw new InvalidConfigurationException("host must not contain NUL characters");
        }
    }

    static void validatePort(int port) throws InvalidConfigurationException {
        if (port < ServerConfigurationDefaults.MIN_PORT || port > ServerConfigurationDefaults.MAX_PORT) {
            throw new InvalidConfigurationException(
                    "port must be between "
                            + ServerConfigurationDefaults.MIN_PORT
                            + " and "
                            + ServerConfigurationDefaults.MAX_PORT
                            + ", got "
                            + port);
        }
    }

    static void validateMaxClientConnections(int maxClientConnections) throws InvalidConfigurationException {
        if (maxClientConnections < ServerConfigurationDefaults.MIN_MAX_CLIENT_CONNECTIONS
                || maxClientConnections > ServerConfigurationDefaults.MAX_MAX_CLIENT_CONNECTIONS) {
            throw new InvalidConfigurationException(
                    "maxClientConnections must be between "
                            + ServerConfigurationDefaults.MIN_MAX_CLIENT_CONNECTIONS
                            + " and "
                            + ServerConfigurationDefaults.MAX_MAX_CLIENT_CONNECTIONS
                            + ", got "
                            + maxClientConnections);
        }
    }

    static void validateWorkerThreads(WorkerThreadConfiguration workerThreads)
            throws InvalidConfigurationException {
        if (workerThreads == null) {
            throw new InvalidConfigurationException("workerThreads must not be null");
        }
        int count = workerThreads.threadCount();
        if (count < ServerConfigurationDefaults.MIN_WORKER_THREADS
                || count > ServerConfigurationDefaults.MAX_WORKER_THREADS) {
            throw new InvalidConfigurationException(
                    "worker thread count must be between "
                            + ServerConfigurationDefaults.MIN_WORKER_THREADS
                            + " and "
                            + ServerConfigurationDefaults.MAX_WORKER_THREADS
                            + ", got "
                            + count);
        }
    }

    static void validatePersistenceDirectory(String persistenceDirectory)
            throws InvalidConfigurationException {
        if (persistenceDirectory == null || persistenceDirectory.isBlank()) {
            throw new InvalidConfigurationException("persistenceDirectory must be a non-blank path");
        }
        try {
            Path.of(persistenceDirectory);
        } catch (InvalidPathException e) {
            throw new InvalidConfigurationException("persistenceDirectory is not a valid path: " + e.getMessage());
        }
    }

    static void validateLoggingLevel(LoggingLevel loggingLevel) throws InvalidConfigurationException {
        if (loggingLevel == null) {
            throw new InvalidConfigurationException("loggingLevel must not be null");
        }
    }

    static void validateTcpBacklog(int tcpBacklog) throws InvalidConfigurationException {
        if (tcpBacklog < 1) {
            throw new InvalidConfigurationException("tcpBacklog must be at least 1, got " + tcpBacklog);
        }
    }
}
