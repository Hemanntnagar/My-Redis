package com.myredis.network;

import com.myredis.config.ServerConfiguration;
import com.myredis.exception.ConnectionException;
import com.myredis.logging.StructuredServerLogger;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TcpListenServerTest {

    @Test
    void startsAcceptsConnectionAndShutsDown() throws Exception {
        int port = freeLocalPort();
        ServerConfiguration configuration =
                ServerConfiguration.builder().host("127.0.0.1").port(port).build();
        StructuredServerLogger logger = new StructuredServerLogger();
        TcpListenServer server = new TcpListenServer(configuration, logger);

        server.start();
        assertTrue(server.isRunning());
        assertTrue(server.boundAddress().getPort() == port);

        try (Socket client = new Socket()) {
            client.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
            assertTrue(client.isConnected());
        }

        awaitActiveCount(server, 0, Duration.ofSeconds(5));

        server.stop();
        assertFalse(server.isRunning());
        assertEquals(0, server.activeConnectionCount());
        assertThrows(IOException.class, () -> new Socket().connect(new InetSocketAddress("127.0.0.1", port), 500));
    }

    @Test
    void startupFailsWhenPortAlreadyInUse() throws Exception {
        int port = freeLocalPort();
        try (ServerSocket holder = new ServerSocket()) {
            holder.bind(new InetSocketAddress("127.0.0.1", port));
            ServerConfiguration configuration =
                    ServerConfiguration.builder().host("127.0.0.1").port(port).build();
            TcpListenServer server = new TcpListenServer(configuration, new StructuredServerLogger());

            ConnectionException failure = assertThrows(ConnectionException.class, server::start);
            assertTrue(failure.clientMessage().contains("Failed to start TCP listener"));
            assertFalse(server.isRunning());
        }
    }

    @Test
    void stopIsIdempotent() throws Exception {
        int port = freeLocalPort();
        ServerConfiguration configuration =
                ServerConfiguration.builder().host("127.0.0.1").port(port).build();
        TcpListenServer server = new TcpListenServer(configuration, new StructuredServerLogger());

        server.start();
        server.stop();
        server.stop();
        assertFalse(server.isRunning());
    }

    @Test
    void acceptsManyConcurrentClientsOnVirtualThreads() throws Exception {
        int port = freeLocalPort();
        int clientCount = 32;
        ServerConfiguration configuration =
                ServerConfiguration.builder()
                        .host("127.0.0.1")
                        .port(port)
                        .maxClientConnections(128)
                        .build();
        TcpListenServer server = new TcpListenServer(configuration, new StructuredServerLogger());

        server.start();
        List<Socket> openClients = new ArrayList<>();
        CountDownLatch connected = new CountDownLatch(clientCount);

        try (ExecutorService connectors = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < clientCount; i++) {
                connectors.submit(
                        () -> {
                            try {
                                Socket socket = new Socket();
                                socket.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
                                synchronized (openClients) {
                                    openClients.add(socket);
                                }
                                connected.countDown();
                            } catch (IOException failure) {
                                throw new AssertionError(failure);
                            }
                        });
            }
            assertTrue(connected.await(15, TimeUnit.SECONDS), "all clients should connect");
        }

        awaitActiveCount(server, clientCount, Duration.ofSeconds(5));
        assertEquals(clientCount, server.activeConnectionCount());
        assertEquals(128, server.maxClientConnections());

        for (Socket socket : openClients) {
            socket.close();
        }
        awaitActiveCount(server, 0, Duration.ofSeconds(5));

        server.stop();
    }

    @Test
    void enforcesMaximumConcurrentConnections() throws Exception {
        int port = freeLocalPort();
        int max = 3;
        ServerConfiguration configuration =
                ServerConfiguration.builder()
                        .host("127.0.0.1")
                        .port(port)
                        .maxClientConnections(max)
                        .build();
        TcpListenServer server = new TcpListenServer(configuration, new StructuredServerLogger());
        server.start();

        List<Socket> holders = new ArrayList<>();
        try {
            for (int i = 0; i < max; i++) {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
                holders.add(socket);
            }
            awaitActiveCount(server, max, Duration.ofSeconds(5));

            Socket rejected = new Socket();
            rejected.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
            rejected.setSoTimeout(2_000);
            Thread.sleep(150);
            assertEquals(max, server.activeConnectionCount());
            assertEquals(-1, rejected.getInputStream().read(), "server should close over-capacity client");

            holders.removeLast().close();
            awaitActiveCount(server, max - 1, Duration.ofSeconds(5));

            Socket replacement = new Socket();
            replacement.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
            holders.add(replacement);
            awaitActiveCount(server, max, Duration.ofSeconds(5));
        } finally {
            for (Socket socket : holders) {
                socket.close();
            }
            server.stop();
        }
    }

    @Test
    void stopReleasesTrackedClientsWithoutLeakingSlots() throws Exception {
        int port = freeLocalPort();
        ServerConfiguration configuration =
                ServerConfiguration.builder().host("127.0.0.1").port(port).maxClientConnections(8).build();
        TcpListenServer server = new TcpListenServer(configuration, new StructuredServerLogger());
        server.start();

        List<Socket> clients = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
            clients.add(socket);
        }
        awaitActiveCount(server, 4, Duration.ofSeconds(5));

        server.stop();
        assertEquals(0, server.activeConnectionCount());

        for (Socket socket : clients) {
            socket.setSoTimeout(500);
            assertEquals(-1, socket.getInputStream().read(), "peer close should surface as EOF");
            socket.close();
        }
    }

    private static void awaitActiveCount(TcpListenServer server, int expected, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (server.activeConnectionCount() != expected) {
            if (System.nanoTime() >= deadline) {
                assertEquals(expected, server.activeConnectionCount());
                return;
            }
            Thread.sleep(25);
        }
    }

    private static int freeLocalPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
