package com.myredis.server;

import com.myredis.config.ServerConfiguration;
import com.myredis.exception.ConnectionException;
import com.myredis.logging.LogContext;
import com.myredis.logging.StructuredServerLogger;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the listening process lifecycle; network accept loops are added in a later phase.
 */
public final class LifecycleDatabaseServer implements DatabaseServer {

    private final ServerConfiguration configuration;
    private final StructuredServerLogger logger;
    private final AtomicBoolean running = new AtomicBoolean();

    public LifecycleDatabaseServer(ServerConfiguration configuration, StructuredServerLogger logger) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void start() throws ConnectionException {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        logger.logInfo(
                "server_listen_prepared",
                "listener lifecycle started",
                LogContext.builder()
                        .put("host", configuration.host())
                        .put("port", String.valueOf(configuration.port()))
                        .build());
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        logger.logInfo("server_listen_stopped", "listener lifecycle stopped", LogContext.empty());
    }

    @Override
    public void close() {
        stop();
    }

    boolean isRunning() {
        return running.get();
    }
}
