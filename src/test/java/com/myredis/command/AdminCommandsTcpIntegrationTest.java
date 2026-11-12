package com.myredis.command;

import com.myredis.config.ServerConfiguration;
import com.myredis.server.ServerBootstrap;
import com.myredis.server.ServerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminCommandsTcpIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void infoTimeCommandAndClientListExposeRuntimeMetrics() throws Exception {
        int port = freeLocalPort();
        ServerConfiguration configuration =
                ServerConfiguration.builder().host("127.0.0.1").port(port).persistenceDirectory(tempDir.toString()).build();
        ServerContext context = new ServerBootstrap().bootstrap(configuration);
        context.databaseServer().start();

        try {
            try (Socket client = new Socket()) {
                client.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
                OutputStream output = client.getOutputStream();
                InputStream input = client.getInputStream();

                output.write(respCommand("PING"));
                output.flush();
                readLine(input);

                output.write(respCommand("INFO", "server"));
                output.flush();
                assertTrue(readBulk(input).contains("uptime_in_seconds"));

                output.write(respCommand("TIME"));
                output.flush();
                assertTrue(readLine(input).startsWith("*"));
                readLine(input);
                readLine(input);
            }

            try (Socket clientListProbe = new Socket()) {
                clientListProbe.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
                OutputStream output = clientListProbe.getOutputStream();
                InputStream input = clientListProbe.getInputStream();
                output.write(respCommand("CLIENT", "LIST"));
                output.flush();
                String clientList = readBulk(input);
                assertTrue(!clientList.isBlank() && clientList.contains("id="));

                output.write(respCommand("CLIENT", "INFO"));
                output.flush();
                assertTrue(readBulk(input).contains("id="));
            }
        } finally {
            context.databaseServer().stop();
        }
    }

    private static byte[] respCommand(String... parts) {
        StringBuilder builder = new StringBuilder();
        builder.append('*').append(parts.length).append("\r\n");
        for (String part : parts) {
            byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
            builder.append('$').append(bytes.length).append("\r\n");
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

    private static String readBulk(InputStream input) throws IOException {
        String lengthLine = readLine(input);
        int length = Integer.parseInt(lengthLine.substring(1));
        byte[] payload = input.readNBytes(length);
        readLine(input);
        return new String(payload, StandardCharsets.UTF_8);
    }

    private static int freeLocalPort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
