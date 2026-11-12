package com.myredis.config;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Canonical default values for {@link ServerConfiguration}.
 */
public final class ServerConfigurationDefaults {

    public static final String HOST = "0.0.0.0";
    public static final int PORT = 6379;
    public static final int TCP_BACKLOG = 511;
    public static final int MAX_CLIENT_CONNECTIONS = 10_000;
    public static final LoggingLevel LOGGING_LEVEL = LoggingLevel.INFO;
    public static final Duration CLIENT_IDLE_TIMEOUT = Duration.ZERO;
    public static final boolean AOF_ENABLED = false;
    public static final com.myredis.persistence.aof.AofFsyncPolicy AOF_FSYNC_POLICY =
            com.myredis.persistence.aof.AofFsyncPolicy.EVERY_SEC;
    public static final long MAX_MEMORY_BYTES = 0L;
    public static final MaxMemoryPolicy MAX_MEMORY_POLICY = MaxMemoryPolicy.NOEVICTION;
    public static final int REPLICATION_BACKLOG_SIZE = 1_024;
    public static final String REPLICA_MASTER_HOST = null;
    public static final int REPLICA_MASTER_PORT = 0;

    public static final int MIN_PORT = 1;
    public static final int MAX_PORT = 65_535;
    public static final int MIN_MAX_CLIENT_CONNECTIONS = 1;
    public static final int MAX_MAX_CLIENT_CONNECTIONS = 1_000_000;
    public static final int MIN_WORKER_THREADS = 1;
    public static final int MAX_WORKER_THREADS = 1_024;

    private ServerConfigurationDefaults() {}

    public static int defaultWorkerThreadCount() {
        return Math.clamp(Runtime.getRuntime().availableProcessors(), MIN_WORKER_THREADS, MAX_WORKER_THREADS);
    }

    public static String defaultPersistenceDirectory() {
        return Path.of(System.getProperty("user.dir", "."), "data").toString();
    }
}
