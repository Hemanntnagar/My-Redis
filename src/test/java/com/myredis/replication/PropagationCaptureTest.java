package com.myredis.replication;

import com.myredis.config.ServerConfiguration;
import com.myredis.protocol.Resp2Decoder;
import com.myredis.protocol.RespArray;
import com.myredis.protocol.RespBulkString;
import com.myredis.server.ServerBootstrap;
import com.myredis.server.ServerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropagationCaptureTest {

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void primarySendsTwoReplicationFrames() throws Exception {
        int port = freePort();
        ServerContext primary =
                new ServerBootstrap()
                        .bootstrap(
                                ServerConfiguration.builder()
                                        .host("127.0.0.1")
                                        .port(port)
                                        .persistenceDirectory(tempDir.toString())
                                        .build());
        primary.databaseServer().start();
        List<byte[]> frames = new CopyOnWriteArrayList<>();
        try (Socket link = open(port)) {
            writeCommand(link, "REPLSYNC", "0");
            assertTrue(readLine(link.getInputStream()).startsWith("+OK"));

            Thread reader =
                    Thread.ofVirtual()
                            .start(
                                    () -> {
                                        try {
                                            captureFrames(link, frames);
                                        } catch (IOException failure) {
                                            throw new IllegalStateException(failure);
                                        }
                                    });
            Thread.sleep(150);

            sendSet(port, "k", "v1");
            Thread.sleep(200);
            sendSet(port, "k", "v2");
            Thread.sleep(500);

            assertEquals(2, frames.size(), "expected two replication frames");
            List<byte[]> first = ReplicaStreamSession.decodeCommandFrame(extractPayload(frames.get(0)));
            List<byte[]> second = ReplicaStreamSession.decodeCommandFrame(extractPayload(frames.get(1)));
            assertEquals("SET", new String(first.get(0), StandardCharsets.UTF_8));
            assertEquals("v1", new String(first.get(2), StandardCharsets.UTF_8));
            assertEquals("SET", new String(second.get(0), StandardCharsets.UTF_8));
            assertEquals("v2", new String(second.get(2), StandardCharsets.UTF_8));
            reader.interrupt();
            reader.join(2_000);
        } finally {
            primary.databaseServer().stop();
        }
    }

    private static byte[] extractPayload(byte[] frame) throws Exception {
        Resp2Decoder decoder = new Resp2Decoder();
        Object decoded = decoder.decodeNext(ByteBuffer.wrap(frame));
        if (!(decoded instanceof RespArray array)) {
            throw new IllegalStateException("expected RESP array");
        }
        if (!(array.elements().getFirst() instanceof RespBulkString bulk)) {
            throw new IllegalStateException("expected bulk replication payload");
        }
        return bulk.data();
    }

    private static void captureFrames(Socket link, List<byte[]> frames) throws IOException {
        ReplicationStreamReader reader = new ReplicationStreamReader();
        InputStream input = link.getInputStream();
        while (!Thread.currentThread().isInterrupted()) {
            Object value = reader.readNext(input);
            if (value == null) {
                break;
            }
            if (value instanceof RespArray array) {
                ByteBuffer encoded = ByteBuffer.allocate(64 * 1024);
                try {
                    com.myredis.protocol.Resp2ArrayEncoder.encode(array, encoded);
                } catch (com.myredis.exception.ProtocolException failure) {
                    throw new IOException(failure);
                }
                encoded.flip();
                byte[] bytes = new byte[encoded.remaining()];
                encoded.get(bytes);
                frames.add(bytes);
            }
        }
    }

    private static void sendSet(int port, String key, String value) throws IOException {
        try (Socket client = open(port)) {
            writeCommand(client, "SET", key, value);
            readLine(client.getInputStream());
        }
    }

    private static Socket open(int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
        return socket;
    }

    private static void writeCommand(Socket socket, String... parts) throws IOException {
        OutputStream output = socket.getOutputStream();
        output.write(respCommand(parts));
        output.flush();
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

    private static int freePort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
