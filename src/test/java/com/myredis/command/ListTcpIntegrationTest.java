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

class ListTcpIntegrationTest {

    @Test
    void listCommandsOverTcp() throws Exception {
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

            output.write(respCommand("RPUSH", "mylist", "a", "b"));
            output.flush();
            assertEquals(":2", readLine(input));

            output.write(respCommand("LPUSH", "mylist", "head"));
            output.flush();
            assertEquals(":3", readLine(input));

            output.write(respCommand("LRANGE", "mylist", "0", "-1"));
            output.flush();
            assertEquals("*3", readLine(input));
            assertEquals("$4", readLine(input));
            assertEquals("head", readLine(input));
            assertEquals("$1", readLine(input));
            assertEquals("a", readLine(input));
            assertEquals("$1", readLine(input));
            assertEquals("b", readLine(input));

            output.write(respCommand("LPOP", "mylist"));
            output.flush();
            assertEquals("$4", readLine(input));
            assertEquals("head", readLine(input));

            output.write(respCommand("LLEN", "missing"));
            output.flush();
            assertEquals(":0", readLine(input));
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
