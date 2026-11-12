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

class DelExistsTypeTcpIntegrationTest {

    @Test
    void delExistsAndTypeOverTcp() throws Exception {
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

            output.write(respCommand("TYPE", "nokey"));
            output.flush();
            assertEquals("+none", readLine(input));

            output.write(respCommand("SET", "k1", "v1"));
            output.flush();
            assertEquals("+OK", readLine(input));

            output.write(respCommand("TYPE", "k1"));
            output.flush();
            assertEquals("+string", readLine(input));

            output.write(respCommand("EXISTS", "k1", "k2"));
            output.flush();
            assertEquals(":1", readLine(input));

            output.write(respCommand("EXISTS", "k2", "k3"));
            output.flush();
            assertEquals(":0", readLine(input));

            output.write(respCommand("DEL", "k2"));
            output.flush();
            assertEquals(":0", readLine(input));

            output.write(respCommand("DEL", "k1", "k1"));
            output.flush();
            assertEquals(":1", readLine(input));

            output.write(respCommand("GET", "k1"));
            output.flush();
            assertEquals("$-1", readLine(input));

            output.write(respCommand("TYPE", "k1"));
            output.flush();
            assertEquals("+none", readLine(input));

            output.write(respCommand("DEL"));
            output.flush();
            String error = readLine(input);
            assertTrue(error.startsWith("-ERR wrong number of arguments for 'del' command"));
        } finally {
            server.stop();
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
