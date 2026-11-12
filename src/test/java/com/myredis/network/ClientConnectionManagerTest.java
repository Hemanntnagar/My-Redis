package com.myredis.network;

import com.myredis.logging.StructuredServerLogger;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConnectionManagerTest {

    @Test
    void registerRemoveAndTrackActiveSessions() throws Exception {
        ClientConnectionManager manager = new ClientConnectionManager(4, Duration.ZERO);

        try (SocketChannel first = openLocalChannel();
                SocketChannel second = openLocalChannel()) {
            ConnectedClientSession a = manager.register(first);
            ConnectedClientSession b = manager.register(second);

            assertNotNull(a);
            assertNotNull(b);
            assertEquals(2, manager.activeCount());
            assertTrue(manager.isTracking(a));

            manager.remove(a);
            assertEquals(1, manager.activeCount());
            assertFalse(manager.isTracking(a));
            assertFalse(first.isOpen());

            manager.remove(b);
            assertEquals(0, manager.activeCount());
        }
    }

    @Test
    void rejectsRegistrationWhenAtCapacity() throws Exception {
        ClientConnectionManager manager = new ClientConnectionManager(2, Duration.ZERO);

        try (SocketChannel first = openLocalChannel();
                SocketChannel second = openLocalChannel();
                SocketChannel third = openLocalChannel()) {
            assertNotNull(manager.register(first));
            assertNotNull(manager.register(second));
            assertNull(manager.register(third));
            assertEquals(2, manager.activeCount());
            assertEquals(2, manager.maxConnections());
        } finally {
            manager.shutdownAll();
        }
    }

    @Test
    void shutdownAllClosesAndClearsSessions() throws Exception {
        ClientConnectionManager manager = new ClientConnectionManager(8, Duration.ZERO);

        try (SocketChannel first = openLocalChannel();
                SocketChannel second = openLocalChannel()) {
            manager.register(first);
            manager.register(second);
            assertEquals(2, manager.activeCount());

            manager.shutdownAll();

            assertEquals(0, manager.activeCount());
            assertFalse(first.isOpen());
            assertFalse(second.isOpen());
        }
    }

    @Test
    void enforceIdleTimeoutsDisconnectsQuietClients() throws Exception {
        ClientConnectionManager manager = new ClientConnectionManager(4, Duration.ofMillis(100));
        StructuredServerLogger logger = new StructuredServerLogger();

        try (SocketChannel channel = openLocalChannel()) {
            ConnectedClientSession session = manager.register(channel);
            assertNotNull(session);
            assertEquals(1, manager.activeCount());

            Thread.sleep(250);
            manager.enforceIdleTimeouts(logger);

            assertEquals(0, manager.activeCount());
            assertFalse(channel.isOpen());
        }
    }

    @Test
    void rejectsNegativeIdleTimeout() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClientConnectionManager(4, Duration.ofMillis(-1)));
    }

    private static SocketChannel openLocalChannel() throws Exception {
        ServerSocket holder = new ServerSocket(0);
        SocketChannel channel = SocketChannel.open();
        channel.connect(new InetSocketAddress("127.0.0.1", holder.getLocalPort()));
        holder.accept();
        holder.close();
        return channel;
    }
}
