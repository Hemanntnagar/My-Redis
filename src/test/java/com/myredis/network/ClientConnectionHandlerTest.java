package com.myredis.network;

import com.myredis.config.ServerConfiguration;
import com.myredis.logging.StructuredServerLogger;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConnectionHandlerTest {

    @Test
    void processesMultipleRequestsOnSingleConnection() throws Exception {
        int port = freeLocalPort();
        ClientRequestProcessor processor =
                (session, commandParts) -> {
                    String command = new String(commandParts.getFirst(), StandardCharsets.UTF_8);
                    if ("PING".equalsIgnoreCase(command)) {
                        return ClientReply.pong();
                    }
                    return ClientReply.error("ERR unknown command");
                };
        TcpListenServer server =
                new TcpListenServer(
                        ServerConfiguration.builder().host("127.0.0.1").port(port).build(),
                        new StructuredServerLogger(),
                        processor);
        server.start();

        try (Socket client = new Socket()) {
            client.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
            OutputStream output = client.getOutputStream();
            InputStream input = client.getInputStream();

            output.write(respCommand("PING"));
            output.flush();
            assertEquals("+PONG", readLine(input));

            output.write(respCommand("PING"));
            output.flush();
            assertEquals("+PONG", readLine(input));
        } finally {
            server.stop();
        }
    }

    @Test
    void releasesSlotAfterClientDisconnect() throws Exception {
        int port = freeLocalPort();
        TcpListenServer server =
                new TcpListenServer(
                        ServerConfiguration.builder().host("127.0.0.1").port(port).build(),
                        new StructuredServerLogger(),
                        (session, commandParts) -> ClientReply.pong());
        server.start();

        try (Socket client = new Socket()) {
            client.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
        }
        awaitActiveCount(server, 0, Duration.ofSeconds(5));
        server.stop();
        assertEquals(0, server.activeConnectionCount());
    }

    @Test
    void handlesFragmentedAndCoalescedWrites() throws Exception {
        int port = freeLocalPort();
        TcpListenServer server =
                new TcpListenServer(
                        ServerConfiguration.builder().host("127.0.0.1").port(port).build(),
                        new StructuredServerLogger(),
                        (session, commandParts) -> ClientReply.pong());
        server.start();

        try (Socket client = new Socket()) {
            client.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
            OutputStream output = client.getOutputStream();
            InputStream input = client.getInputStream();

            byte[] once = respCommand("PING");
            byte[] twice = new byte[once.length * 2];
            System.arraycopy(once, 0, twice, 0, once.length);
            System.arraycopy(once, 0, twice, once.length, once.length);
            output.write(twice);
            output.flush();
            assertEquals("+PONG", readLine(input));
            assertEquals("+PONG", readLine(input));

            for (byte bite : once) {
                output.write(bite);
                output.flush();
            }
            assertEquals("+PONG", readLine(input));
        } finally {
            server.stop();
        }
    }

    @Test
    void echoesLargeBulkPayloadOverFragmentedTcp() throws Exception {
        int port = freeLocalPort();
        int payloadSize = 48 * 1024;
        byte[] payload = new byte[payloadSize];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) ('a' + (index % 26));
        }
        TcpListenServer server =
                new TcpListenServer(
                        ServerConfiguration.builder().host("127.0.0.1").port(port).build(),
                        new StructuredServerLogger(),
                        (session, commandParts) -> {
                            if (commandParts.size() == 2) {
                                return new ClientReply.BulkString(commandParts.get(1));
                            }
                            return ClientReply.error("ERR expected bulk argument");
                        });
        server.start();

        try (Socket client = new Socket()) {
            client.connect(new InetSocketAddress("127.0.0.1", port), 5_000);
            OutputStream output = client.getOutputStream();
            InputStream input = client.getInputStream();

            byte[] frame = respBulkCommand("ECHO", payload);
            int chunkSize = 97;
            for (int offset = 0; offset < frame.length; offset += chunkSize) {
                int length = Math.min(chunkSize, frame.length - offset);
                output.write(frame, offset, length);
                output.flush();
            }

            assertEquals("$" + payloadSize, readLine(input));
            byte[] received = readBulkPayload(input, payloadSize);
            assertEquals(payload.length, received.length);
            for (int index = 0; index < payload.length; index++) {
                assertEquals(payload[index], received[index]);
            }
        } finally {
            server.stop();
        }
    }

    @Test
    void repliesWithProtocolErrorForNonArrayPayload() throws Exception {
        int port = freeLocalPort();
        TcpListenServer server =
                new TcpListenServer(
                        ServerConfiguration.builder().host("127.0.0.1").port(port).build(),
                        new StructuredServerLogger(),
                        (session, commandParts) -> ClientReply.pong());
        server.start();

        try (Socket client = new Socket()) {
            client.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
            client.getOutputStream().write("+OK\r\n".getBytes(StandardCharsets.UTF_8));
            client.getOutputStream().flush();
            String line = readLine(client.getInputStream());
            assertTrue(line.startsWith("-"), "protocol errors must use RESP error prefix");
        } finally {
            server.stop();
        }
    }

    private static byte[] respBulkCommand(String command, byte[] payload) {
        StringBuilder builder = new StringBuilder();
        builder.append("*2\r\n");
        builder.append('$').append(command.length()).append("\r\n").append(command).append("\r\n");
        builder.append('$').append(payload.length).append("\r\n");
        byte[] header = builder.toString().getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[header.length + payload.length + 2];
        System.arraycopy(header, 0, frame, 0, header.length);
        System.arraycopy(payload, 0, frame, header.length, payload.length);
        frame[frame.length - 2] = '\r';
        frame[frame.length - 1] = '\n';
        return frame;
    }

    private static byte[] readBulkPayload(InputStream input, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(data, offset, length - offset);
            if (read < 0) {
                break;
            }
            offset += read;
        }
        expectCrlf(input);
        return data;
    }

    private static void expectCrlf(InputStream input) throws IOException {
        assertEquals('\r', input.read());
        assertEquals('\n', input.read());
    }

    private static byte[] respCommand(String... parts) {
        StringBuilder builder = new StringBuilder();
        builder.append('*').append(parts.length).append("\r\n");
        for (String part : parts) {
            builder.append('$').append(part.length()).append("\r\n").append(part).append("\r\n");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String readLine(InputStream input) throws IOException {
        StringBuilder builder = new StringBuilder();
        int previous = -1;
        while (true) {
            int value = input.read();
            if (value < 0) {
                break;
            }
            char ch = (char) value;
            if (previous == '\r' && ch == '\n') {
                builder.deleteCharAt(builder.length() - 1);
                break;
            }
            builder.append(ch);
            previous = value;
        }
        return builder.toString();
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
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
