package com.myredis.network;

import com.myredis.config.ServerConfiguration;
import com.myredis.exception.ConnectionException;
import com.myredis.logging.LogContext;
import com.myredis.logging.StructuredServerLogger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Blocking NIO accept loop bound to configured host and port.
 * Each accepted client is processed on a dedicated virtual thread.
 */
public final class TcpListenServer implements AutoCloseable {

    private final ServerConfiguration configuration;
    private final StructuredServerLogger logger;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ActiveConnectionRegistry connections;

    private final ExecutorService clientExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private volatile ServerSocketChannel serverChannel;
    private volatile Thread acceptThread;
    private volatile InetSocketAddress boundAddress;

    private final ClientRequestProcessor requestProcessor;

    public TcpListenServer(ServerConfiguration configuration, StructuredServerLogger logger) {
        this(configuration, logger, new UnconfiguredClientRequestProcessor());
    }

    public TcpListenServer(
            ServerConfiguration configuration,
            StructuredServerLogger logger,
            ClientRequestProcessor requestProcessor) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.requestProcessor = Objects.requireNonNull(requestProcessor, "requestProcessor");
        this.connections = new ActiveConnectionRegistry(configuration.maxClientConnections());
    }

    public void start() throws ConnectionException {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        ServerSocketChannel channel = null;
        try {
            channel = ServerSocketChannel.open();
            channel.configureBlocking(true);
            InetSocketAddress listenAddress = resolveListenAddress(configuration.host(), configuration.port());
            channel.bind(listenAddress, configuration.tcpBacklog());
            boundAddress = (InetSocketAddress) channel.getLocalAddress();

            logger.logInfo(
                    "server_listen_bound",
                    "TCP listener bound",
                    LogContext.builder()
                            .put("host", boundAddress.getHostString())
                            .put("port", String.valueOf(boundAddress.getPort()))
                            .put("backlog", String.valueOf(configuration.tcpBacklog()))
                            .put("max_connections", String.valueOf(configuration.maxClientConnections()))
                            .build());

            serverChannel = channel;

            Thread acceptLoop =
                    Thread.ofVirtual().name("myredis-accept").unstarted(this::acceptLoop);
            acceptLoop.setDaemon(true);
            acceptThread = acceptLoop;
            acceptLoop.start();

            logger.logInfo("server_listen_started", "TCP listener accepting connections", LogContext.empty());
        } catch (IOException failure) {
            running.set(false);
            serverChannel = null;
            boundAddress = null;
            closeQuietly(channel);
            throw new ConnectionException(
                    "Failed to start TCP listener",
                    listenTarget(configuration),
                    failure);
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        logger.logInfo("server_listen_stopping", "stopping TCP listener", LogContext.empty());

        ServerSocketChannel channel = serverChannel;
        serverChannel = null;
        closeQuietly(channel);

        Thread thread = acceptThread;
        acceptThread = null;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        connections.closeAllSessions();

        clientExecutor.close();

        boundAddress = null;

        logger.logInfo(
                "server_listen_stopped",
                "TCP listener stopped",
                LogContext.builder().put("active_connections", String.valueOf(activeConnectionCount())).build());
    }

    @Override
    public void close() {
        stop();
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Effective bind address after {@link #start()}; {@code null} when not listening.
     */
    public InetSocketAddress boundAddress() {
        return boundAddress;
    }

    /** Number of clients currently registered with the listener. */
    public int activeConnectionCount() {
        return connections.activeCount();
    }

    /** Configured upper bound on simultaneous client connections. */
    public int maxClientConnections() {
        return connections.maxConnections();
    }

    private void acceptLoop() {
        while (running.get()) {
            ServerSocketChannel channel = serverChannel;
            if (channel == null) {
                return;
            }
            try {
                SocketChannel client = channel.accept();
                if (client == null) {
                    continue;
                }
                handleAcceptedClient(client);
            } catch (ClosedChannelException closed) {
                if (running.get()) {
                    logger.logInfo(
                            "server_listen_accept_closed",
                            "accept loop ended after listener closed",
                            LogContext.empty());
                }
                return;
            } catch (IOException failure) {
                if (running.get()) {
                    logger.logWarning(
                            "server_listen_accept_failed",
                            "accept failed",
                            LogContext.builder().put("detail", failure.getMessage()).build());
                }
                return;
            }
        }
    }

    private void handleAcceptedClient(SocketChannel client) {
        if (!running.get()) {
            closeQuietly(client);
            return;
        }

        ConnectedClientSession session = connections.tryRegister(client);
        if (session == null) {
            closeQuietly(client);
            logger.logWarning(
                    "client_rejected",
                    "connection refused at capacity",
                    LogContext.builder()
                            .put("max_connections", String.valueOf(configuration.maxClientConnections()))
                            .build());
            return;
        }

        logger.logInfo(
                "client_connected",
                "client accepted",
                LogContext.builder()
                        .put("session_id", session.id())
                        .put("remote", String.valueOf(session.remoteAddress()))
                        .put("active_connections", String.valueOf(connections.activeCount()))
                        .build());

        ClientConnectionHandler handler =
                new ClientConnectionHandler(session, connections, logger, running::get, requestProcessor);
        clientExecutor.submit(handler);
    }

    private static InetSocketAddress resolveListenAddress(String host, int port) {
        if ("0.0.0.0".equals(host)) {
            return new InetSocketAddress(port);
        }
        return new InetSocketAddress(host, port);
    }

    private static String listenTarget(ServerConfiguration configuration) {
        return configuration.host() + ":" + configuration.port();
    }

    private static void closeQuietly(ServerSocketChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // Best effort during shutdown.
        }
    }

    private static void closeQuietly(SocketChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // Best effort during shutdown.
        }
    }
}
