package com.myredis.network;

import com.myredis.logging.LogContext;
import com.myredis.logging.StructuredServerLogger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Blocking read loop for one client; each instance runs on its own virtual thread.
 */
final class ClientConnectionHandler implements Runnable {

    private final ConnectedClientSession session;
    private final ActiveConnectionRegistry registry;
    private final StructuredServerLogger logger;
    private final BooleanSupplier serverRunning;

    ClientConnectionHandler(
            ConnectedClientSession session,
            ActiveConnectionRegistry registry,
            StructuredServerLogger logger,
            BooleanSupplier serverRunning) {
        this.session = Objects.requireNonNull(session, "session");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.serverRunning = Objects.requireNonNull(serverRunning, "serverRunning");
    }

    @Override
    public void run() {
        SocketChannel channel = session.channel();
        try {
            ByteBuffer sink = ByteBuffer.allocate(256);
            while (serverRunning.getAsBoolean() && channel.isOpen()) {
                int read = channel.read(sink);
                if (read < 0) {
                    break;
                }
                if (read > 0) {
                    sink.clear();
                }
            }
        } catch (IOException ignored) {
            // Peer closed or listener is shutting down.
        } finally {
            registry.unregister(session);
            logger.logInfo(
                    "client_disconnected",
                    "client connection closed",
                    LogContext.builder().put("remote", String.valueOf(session.remoteAddress())).build());
        }
    }
}
