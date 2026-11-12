package com.myredis.replication;

import com.myredis.command.CommandDispatcher;
import com.myredis.config.ServerConfiguration;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.protocol.Resp2Decoder;
import com.myredis.protocol.RespArray;
import com.myredis.protocol.RespBulkString;
import com.myredis.persistence.snapshot.DatabaseSnapshot;
import com.myredis.persistence.snapshot.KeyspaceSnapshotter;
import com.myredis.persistence.snapshot.SnapshotReader;
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
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplicationTcpIntegrationTest {

    @TempDir
    java.nio.file.Path tempDir;

    private final List<Socket> openLinks = new ArrayList<>();
    private final List<Thread> followers = new ArrayList<>();

    @Test
    void replSyncHandshakeReturnsOkOnPrimary() throws Exception {
        int primaryPort = freeLocalPort();
        ServerContext primary =
                new ServerBootstrap()
                        .bootstrap(
                                ServerConfiguration.builder()
                                        .host("127.0.0.1")
                                        .port(primaryPort)
                                        .persistenceDirectory(tempDir.resolve("handshake").toString())
                                        .build());
        primary.databaseServer().start();
        try (Socket client = open("127.0.0.1", primaryPort)) {
            writeCommand(client, "REPLSYNC", "0");
            assertTrue(readLine(client.getInputStream()).startsWith("+OK"));
        } finally {
            primary.databaseServer().stop();
        }
    }

    @Test
    void primaryReplicatesWritesToMultipleReplicas() throws Exception {
        int primaryPort = freeLocalPort();
        int replicaOnePort = freeLocalPort();
        int replicaTwoPort = freeLocalPort();

        ServerContext primary =
                bootstrapServer(primaryPort, tempDir.resolve("primary").toString());
        ServerContext replicaOne =
                bootstrapServer(replicaOnePort, tempDir.resolve("replica1").toString());
        ServerContext replicaTwo =
                bootstrapServer(replicaTwoPort, tempDir.resolve("replica2").toString());

        primary.databaseServer().start();
        replicaOne.databaseServer().start();
        replicaTwo.databaseServer().start();

        try {
            linkReplicaToPrimary(replicaOne, primaryPort, 0);
            linkReplicaToPrimary(replicaTwo, primaryPort, 0);
            Thread.sleep(300);

            try (Socket primaryClient = open("127.0.0.1", primaryPort)) {
                writeCommand(primaryClient, "SET", "replicated", "value");
                assertEquals("+OK", readLine(primaryClient.getInputStream()));
            }

            waitForKey(replicaOnePort, "replicated", "value");
            waitForKey(replicaTwoPort, "replicated", "value");

            try (Socket primaryClient = open("127.0.0.1", primaryPort)) {
                writeCommand(primaryClient, "SET", "replicated", "updated");
                assertEquals("+OK", readLine(primaryClient.getInputStream()));
            }
            waitForKey(replicaOnePort, "replicated", "updated");
            waitForKey(replicaTwoPort, "replicated", "updated");
        } finally {
            shutdownFollowers();
            replicaTwo.databaseServer().stop();
            replicaOne.databaseServer().stop();
            primary.databaseServer().stop();
        }
    }

    @Test
    void partialResyncReplaysBacklogAfterReconnect() throws Exception {
        int primaryPort = freeLocalPort();
        int replicaPort = freeLocalPort();
        ServerContext primary =
                new ServerBootstrap()
                        .bootstrap(
                                ServerConfiguration.builder()
                                        .host("127.0.0.1")
                                        .port(primaryPort)
                                        .replicationBacklogSize(128)
                                        .persistenceDirectory(tempDir.resolve("primary-partial").toString())
                                        .build());
        ServerContext replica =
                bootstrapServer(replicaPort, tempDir.resolve("replica-partial").toString());
        primary.databaseServer().start();
        replica.databaseServer().start();
        try {
            Socket firstLink = linkReplicaToPrimary(replica, primaryPort, 0);
            Thread.sleep(300);
            try (Socket primaryClient = open("127.0.0.1", primaryPort)) {
                writeCommand(primaryClient, "SET", "partial", "one");
                readLine(primaryClient.getInputStream());
            }
            Thread.sleep(300);
            firstLink.close();
            shutdownFollowers();
            Thread.sleep(200);
            long lastKnownOffset =
                    primary.collaborators().replication().replicationOffset();
            linkReplicaToPrimary(replica, primaryPort, lastKnownOffset);
            Thread.sleep(300);
            try (Socket primaryClient = open("127.0.0.1", primaryPort)) {
                writeCommand(primaryClient, "SET", "partial", "two");
                readLine(primaryClient.getInputStream());
            }
            Thread.sleep(500);
            assertEquals("two", getKey(replicaPort, "partial"));
        } finally {
            shutdownFollowers();
            replica.databaseServer().stop();
            primary.databaseServer().stop();
        }
    }

    private ServerContext bootstrapServer(int port, String persistenceDirectory) throws Exception {
        return new ServerBootstrap()
                .bootstrap(
                        ServerConfiguration.builder()
                                .host("127.0.0.1")
                                .port(port)
                                .persistenceDirectory(persistenceDirectory)
                                .build());
    }

    private Socket linkReplicaToPrimary(ServerContext replica, int primaryPort, long replicaOffset)
            throws Exception {
        Socket link = open("127.0.0.1", primaryPort);
        writeCommand(link, "REPLSYNC", Long.toString(replicaOffset));
        CommandDispatcher apply = replica.collaborators().replicationApplyDispatcher();
        drainHandshakeOk(link, replica, apply);
        openLinks.add(link);
        Thread follower =
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    try {
                                        followReplicationStream(link, apply);
                                    } catch (IOException failure) {
                                        throw new IllegalStateException(failure);
                                    }
                                });
        followers.add(follower);
        return link;
    }

    private static void followReplicationStream(Socket link, CommandDispatcher apply) throws IOException {
        ReplicationStreamReader reader = new ReplicationStreamReader();
        InputStream input = link.getInputStream();
        while (!link.isClosed()) {
            Object value = reader.readNext(input);
            if (value == null) {
                break;
            }
            if (value instanceof RespArray array) {
                applyReplicationFrame(apply, array);
            }
        }
    }

    private void drainHandshakeOk(Socket link, ServerContext replica, CommandDispatcher apply) throws IOException {
        InputStream input = link.getInputStream();
        ReplicationStreamReader reader = new ReplicationStreamReader();
        while (true) {
            input.mark(8_192);
            int next = input.read();
            if (next < 0) {
                throw new IOException("connection closed before replication handshake completed");
            }
            if (next == '+') {
                StringBuilder line = new StringBuilder();
                line.append((char) next);
                appendLineRemainder(input, line);
                assertTrue(line.toString().startsWith("+OK"), () -> "unexpected handshake: " + line);
                return;
            }
            input.reset();
            Object value = reader.readNext(input);
            if (value instanceof RespArray array) {
                applySyncFrame(replica, apply, array);
            }
        }
    }

    private static void appendLineRemainder(InputStream input, StringBuilder line) throws IOException {
        int previous = '\r';
        while (true) {
            int value = input.read();
            if (value < 0) {
                break;
            }
            char ch = (char) value;
            if (previous == '\r' && ch == '\n') {
                line.deleteCharAt(line.length() - 1);
                break;
            }
            line.append(ch);
            previous = value;
        }
    }

    private static void applySyncFrame(ServerContext replica, CommandDispatcher apply, RespArray frame)
            throws IOException {
        if (isSnapshotFrame(frame)) {
            applySnapshot(replica, frame);
            return;
        }
        applyReplicationFrame(apply, frame);
    }

    private static boolean isSnapshotFrame(RespArray array) {
        return array.elements().size() == 2
                && array.elements().get(0) instanceof RespBulkString marker
                && "SNAPSHOT".equals(new String(marker.data(), StandardCharsets.UTF_8));
    }

    private static void applySnapshot(ServerContext replica, RespArray snapshotFrame) throws IOException {
        if (!(snapshotFrame.elements().get(1) instanceof RespBulkString snapshotBulk)) {
            throw new IOException("invalid replication snapshot payload");
        }
        if (!(replica.collaborators().replication() instanceof InMemoryReplicationCoordinator coordinator)) {
            throw new IOException("replication coordinator does not expose keyspace");
        }
        try {
            DatabaseSnapshot snapshot = SnapshotReader.decode(snapshotBulk.data());
            KeyspaceSnapshotter.restore(
                    coordinator.store(), coordinator.store().expirationService(), snapshot);
        } catch (Exception failure) {
            throw new IOException("failed to apply replication snapshot", failure);
        }
    }

    private static void applyReplicationFrame(CommandDispatcher apply, RespArray frame) {
        if (frame.elements().size() != 1 || !(frame.elements().getFirst() instanceof RespBulkString bulk)) {
            return;
        }
        List<byte[]> parts = ReplicaStreamSession.decodeCommandFrame(bulk.data());
        String commandName = new String(parts.getFirst(), StandardCharsets.UTF_8);
        List<byte[]> arguments = parts.size() == 1 ? List.of() : parts.subList(1, parts.size());
        try {
            ClientReply reply = apply.dispatch(replicaSession(), commandName, arguments);
            if (reply instanceof ClientReply.Error error) {
                throw new IllegalStateException(error.message());
            }
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static ClientSession replicaSession() {
        return new ClientSession() {
            @Override
            public String id() {
                return "test-replica-follower";
            }

            @Override
            public SocketChannel channel() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private void shutdownFollowers() throws InterruptedException, IOException {
        for (Socket link : openLinks) {
            link.close();
        }
        openLinks.clear();
        for (Thread follower : followers) {
            follower.join(2_000);
        }
        followers.clear();
    }

    private static void waitForKey(int port, String key, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            String value = getKey(port, key);
            if (expected.equals(value)) {
                return;
            }
            Thread.sleep(50);
        }
        assertEquals(expected, getKey(port, key));
    }

    private static String getKey(int port, String key) throws IOException {
        try (Socket client = open("127.0.0.1", port)) {
            writeCommand(client, "GET", key);
            InputStream input = client.getInputStream();
            String line = readLine(input);
            if ("$-1".equals(line)) {
                return null;
            }
            int length = Integer.parseInt(line.substring(1));
            byte[] payload = input.readNBytes(length);
            readLine(input);
            return new String(payload, StandardCharsets.UTF_8);
        }
    }

    private static Socket open(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 2_000);
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

    private static int freeLocalPort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
