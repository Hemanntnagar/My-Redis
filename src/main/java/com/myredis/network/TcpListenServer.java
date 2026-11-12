package com.myredis.network;

import com.myredis.config.ServerConfiguration;
import com.myredis.exception.ConnectionException;
import com.myredis.logging.LogContext;
import com.myredis.logging.StructuredServerLogger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Blocking NIO accept loop bound to configured host and port.
 */
public final class TcpListenServer implements AutoCloseable {

    private final ServerConfiguration configuration;
    private final StructuredServerLogger logger;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicInteger activeConnections = new AtomicInteger();
    private final Set<SocketChannel> openClients = ConcurrentHashMap.newKeySet();

    private volatile ServerSocketChannel serverChannel;
    private volatile Thread acceptThread;
    private volatile InetSocketAddress boundAddress;

    public TcpListenServer(ServerConfiguration configuration, StructuredServerLogger logger) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.logger = Objects.requireNonNull(logger, "logger");
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
                            .build());

            serverChannel = channel;

            Thread acceptLoop =
                    new Thread(this::acceptLoop, "myredis-accept");
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

        for (SocketChannel client : openClients) {
            closeQuietly(client);
        }
        openClients.clear();
        activeConnections.set(0);
        boundAddress = null;

        logger.logInfo("server_listen_stopped", "TCP listener stopped", LogContext.empty());
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

        int active = activeConnections.incrementAndGet();
        if (active > configuration.maxClientConnections()) {
            activeConnections.decrementAndGet();
            closeQuietly(client);
            logger.logWarning(
                    "client_rejected",
                    "connection refused at capacity",
                    LogContext.builder()
                            .put("max_connections", String.valueOf(configuration.maxClientConnections()))
                            .build());
            return;
        }

        openClients.add(client);
        SocketAddress remote = client.socket().getRemoteSocketAddress();
        logger.logInfo(
                "client_connected",
                "client accepted",
                LogContext.builder().put("remote", String.valueOf(remote)).build());

        Thread handler =
                new Thread(
                        () -> {
                            try {
                                ByteBuffer sink = ByteBuffer.allocate(256);
                                while (running.get() && client.isOpen()) {
                                    int read = client.read(sink);
                                    if (read <= 0) {
                                        break;
                                    }
                                    sink.clear();
                                }
                            } catch (IOException ignored) {
                                // Client closed or listener shutting down.
                            } finally {
                                openClients.remove(client);
                                closeQuietly(client);
                                activeConnections.decrementAndGet();
                                logger.logInfo(
                                        "client_disconnected",
                                        "client connection closed",
                                        LogContext.builder().put("remote", String.valueOf(remote)).build());
                            }
                        },
                        "myredis-client-" + remote);
        handler.setDaemon(true);
        handler.start();
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
