package com.myredis.server;

import com.myredis.command.CommandClientRequestProcessor;
import com.myredis.config.ServerConfiguration;
import com.myredis.exception.ConnectionException;
import com.myredis.logging.StructuredServerLogger;
import com.myredis.network.ClientRequestProcessor;
import com.myredis.network.TcpListenServer;

import java.util.Objects;

/**
 * Manages the listening process lifecycle and delegates TCP I/O to {@link TcpListenServer}.
 */
public final class LifecycleDatabaseServer implements DatabaseServer {

    private final TcpListenServer listenServer;

    public LifecycleDatabaseServer(ServerConfiguration configuration, StructuredServerLogger logger) {
        this(configuration, logger, CommandClientRequestProcessor.withBuiltinCommands());
    }

    public LifecycleDatabaseServer(
            ServerConfiguration configuration,
            StructuredServerLogger logger,
            ClientRequestProcessor requestProcessor) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(requestProcessor, "requestProcessor");
        this.listenServer = new TcpListenServer(configuration, logger, requestProcessor);
    }

    @Override
    public void start() throws ConnectionException {
        listenServer.start();
    }

    @Override
    public void stop() {
        listenServer.stop();
    }

    @Override
    public void close() {
        listenServer.close();
    }

    boolean isRunning() {
        return listenServer.isRunning();
    }

    TcpListenServer listenServer() {
        return listenServer;
    }
}
