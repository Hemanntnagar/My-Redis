package com.myredis.server;

import com.myredis.config.ServerConfiguration;
import com.myredis.logging.LogContext;
import com.myredis.logging.StructuredServerLogger;

import java.util.Objects;

/**
 * Emits operator-facing startup details on stdout and through structured logs.
 */
public final class ServerStartupReporter {

    private final StructuredServerLogger logger;

    public ServerStartupReporter(StructuredServerLogger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void report(ServerConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");

        String bind = configuration.host() + ":" + configuration.port();
        System.out.println(ServerMetadata.NAME + " " + ServerMetadata.VERSION);
        System.out.println("  listen bind: " + bind);
        System.out.println("  max clients: " + configuration.maxClientConnections());
        System.out.println("  worker threads: " + configuration.workerThreads().threadCount());
        System.out.println("  persistence dir: " + configuration.persistenceDirectory());
        System.out.println("  logging level: " + configuration.loggingLevel());
        System.out.println("  tcp backlog: " + configuration.tcpBacklog());
        System.out.println("  (command pipeline not wired yet)");

        logger.logInfo(
                "server_start",
                ServerMetadata.NAME + " ready to accept connections",
                LogContext.builder()
                        .put("version", ServerMetadata.VERSION)
                        .put("bind", bind)
                        .put("max_clients", String.valueOf(configuration.maxClientConnections()))
                        .put("worker_threads", String.valueOf(configuration.workerThreads().threadCount()))
                        .put("logging_level", configuration.loggingLevel().name())
                        .build());
    }
}
