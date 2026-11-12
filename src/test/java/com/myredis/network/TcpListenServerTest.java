package com.myredis.network;

import com.myredis.config.ServerConfiguration;
import com.myredis.exception.ConnectionException;
import com.myredis.logging.StructuredServerLogger;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

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

        server.stop();
        assertFalse(server.isRunning());
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

    private static int freeLocalPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
