package com.myredis.command;

import com.myredis.config.ServerConfiguration;
import com.myredis.logging.StructuredServerLogger;
import com.myredis.network.TcpListenServer;
import com.myredis.storage.InMemoryKeyValueStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetGetTcpIntegrationTest {

    @Test
    void setAndGetOverTcp() throws Exception {
        int port = freeLocalPort();
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        TcpListenServer server =
                new TcpListenServer(
                        ServerConfiguration.builder().host("127.0.0.1").port(port).build(),
                        new StructuredServerLogger(),
                        CommandClientRequestProcessor.withBuiltinCommands(store));
        server.start();

        try (Socket client = new Socket()) {
            client.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
            OutputStream output = client.getOutputStream();
            InputStream input = client.getInputStream();

            output.write(respCommand("SET", "greeting", "hello"));
            output.flush();
            assertEquals("+OK", readLine(input));

            output.write(respCommand("GET", "greeting"));
            output.flush();
            assertEquals("$5", readLine(input));
            assertEquals("hello", readLine(input));

            output.write(respCommand("GET", "missing"));
            output.flush();
            assertEquals("$-1", readLine(input));

            output.write(respCommand("SET", "greeting", "world"));
            output.flush();
            assertEquals("+OK", readLine(input));

            output.write(respCommand("GET", "greeting"));
            output.flush();
            assertEquals("$5", readLine(input));
            assertEquals("world", readLine(input));

            byte[] key = "bin".getBytes(StandardCharsets.UTF_8);
            byte[] value = new byte[] {'a', 0, 'b', '\n'};
            output.write(respBulkCommand("SET", key, value));
            output.flush();
            assertEquals("+OK", readLine(input));

            output.write(respBulkCommand("GET", key));
            output.flush();
            assertEquals("$4", readLine(input));
            assertBulkPayload(input, value);

            output.write(respCommand("SET", "greeting"));
            output.flush();
            String error = readLine(input);
            assertTrue(error.startsWith("-ERR wrong number of arguments for 'set' command"));
        } finally {
            server.stop();
        }
    }

    private static void assertBulkPayload(InputStream input, byte[] expected) throws IOException {
        byte[] buffer = new byte[expected.length];
        int offset = 0;
        while (offset < expected.length) {
            int read = input.read(buffer, offset, expected.length - offset);
            if (read < 0) {
                throw new IOException("unexpected end of stream");
            }
            offset += read;
        }
        assertEquals('\r', input.read());
        assertEquals('\n', input.read());
        assertEquals(0, input.available());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], buffer[i], "byte at " + i);
        }
    }

    private static byte[] respCommand(String... parts) {
        StringBuilder builder = new StringBuilder();
        builder.append('*').append(parts.length).append("\r\n");
        for (String part : parts) {
            builder.append('$').append(part.getBytes(StandardCharsets.UTF_8).length).append("\r\n");
            builder.append(part).append("\r\n");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] respBulkCommand(String command, byte[]... parts) {
        int count = 1 + parts.length;
        StringBuilder builder = new StringBuilder();
        builder.append('*').append(count).append("\r\n");
        byte[] commandBytes = command.getBytes(StandardCharsets.UTF_8);
        builder.append('$').append(commandBytes.length).append("\r\n");
        builder.append(command).append("\r\n");
        for (byte[] part : parts) {
            builder.append('$').append(part.length).append("\r\n");
            for (byte b : part) {
                builder.append((char) (b & 0xFF));
            }
            builder.append("\r\n");
        }
        return builder.toString().getBytes(StandardCharsets.ISO_8859_1);
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

    private static int freeLocalPort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
