package com.myredis.replication;

import com.myredis.command.CommandDispatcher;
import com.myredis.exception.CommandException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.persistence.aof.AofRespCommandCodec;
import com.myredis.persistence.snapshot.DatabaseSnapshot;
import com.myredis.persistence.snapshot.KeyspaceSnapshotter;
import com.myredis.persistence.snapshot.SnapshotReader;
import com.myredis.protocol.Resp2Decoder;
import com.myredis.protocol.RespArray;
import com.myredis.protocol.RespBulkString;
import com.myredis.protocol.RespError;
import com.myredis.protocol.RespSimpleString;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Outbound replication link from a replica process to its primary.
 */
public final class ReplicaUpstreamClient implements AutoCloseable {

    private final String masterHost;
    private final int masterPort;
    private final CommandDispatcher dispatcher;
    private final InMemoryReplicationCoordinator coordinator;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong replicationOffset = new AtomicLong(0);
    private volatile Thread worker;
    private volatile Socket socket;
    private volatile CountDownLatch syncedLatch = new CountDownLatch(1);

    public ReplicaUpstreamClient(
            String masterHost,
            int masterPort,
            CommandDispatcher dispatcher,
            InMemoryReplicationCoordinator coordinator) {
        this.masterHost = Objects.requireNonNull(masterHost, "masterHost");
        this.masterPort = masterPort;
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    public void start() {
        closeInternal(false);
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread thread =
                Thread.ofVirtual()
                        .name("myredis-replica-upstream")
                        .unstarted(this::replicationWorkerLoop);
        thread.setDaemon(true);
        worker = thread;
        thread.start();
    }

    public boolean awaitHandshake(long timeoutMillis) throws InterruptedException {
        return syncedLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        closeInternal(true);
    }

    private void closeInternal(boolean interruptWorker) {
        running.set(false);
        Socket active = socket;
        socket = null;
        if (active != null) {
            try {
                active.close();
            } catch (IOException ignored) {
            }
        }
        if (interruptWorker) {
            Thread thread = worker;
            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    private void replicationWorkerLoop() {
        while (running.get()) {
            try {
                ReplicationStreamReader reader = new ReplicationStreamReader();
                openConnection();
                performHandshake(reader);
                replicateLiveStream(reader);
            } catch (Exception failure) {
                sleepBriefly();
            }
        }
    }

    private void openConnection() throws IOException {
        syncedLatch = new CountDownLatch(1);
        Socket connected = new Socket();
        connected.connect(new InetSocketAddress(masterHost, masterPort), 5_000);
        connected.setTcpNoDelay(true);
        socket = connected;
        writeRespCommand(connected, List.of("REPLSYNC", Long.toString(replicationOffset.get())));
    }

    private void performHandshake(ReplicationStreamReader reader) throws IOException, CommandException {
        Socket activeSocket = socket;
        if (activeSocket == null) {
            throw new IOException("replication socket not open");
        }
        InputStream input = activeSocket.getInputStream();
        String firstLine = readLine(input);
        if (firstLine != null && firstLine.startsWith("+OK")) {
            parseOkLine(firstLine);
            signalHandshakeComplete();
            return;
        }
        if (firstLine != null && !firstLine.startsWith("+OK")) {
            reader.seed((firstLine + "\r\n").getBytes(StandardCharsets.US_ASCII));
        }
        while (true) {
            Object value = reader.readNext(input);
            if (value == null) {
                throw new IOException("replication handshake closed");
            }
            if (value instanceof RespError error) {
                throw new CommandException(error.message());
            }
            if (value instanceof RespSimpleString simple && simple.value().startsWith("OK")) {
                parseOkSimple(simple.value());
                signalHandshakeComplete();
                return;
            }
            if (value instanceof RespArray array) {
                if (isSnapshotFrame(array)) {
                    applySnapshot(extractSnapshot(array));
                } else {
                    applyReplicationCommand(array);
                }
            }
        }
    }

    private void replicateLiveStream(ReplicationStreamReader reader) throws IOException, CommandException {
        Socket activeSocket = socket;
        if (activeSocket == null) {
            return;
        }
        while (running.get() && !activeSocket.isClosed()) {
            Object value = reader.readNext(activeSocket.getInputStream());
            if (value == null) {
                break;
            }
            if (value instanceof RespArray array) {
                applyReplicationCommand(array);
                replicationOffset.incrementAndGet();
            }
        }
    }

    private void parseOkLine(String line) {
        String payload = line.substring(1).trim();
        if (payload.length() > 2 && payload.startsWith("OK")) {
            String tail = payload.substring(2).trim();
            if (!tail.isEmpty()) {
                replicationOffset.set(Long.parseLong(tail));
            }
        }
    }

    private void parseOkSimple(String value) {
        String tail = value.length() > 2 ? value.substring(2).trim() : "";
        if (!tail.isEmpty()) {
            replicationOffset.set(Long.parseLong(tail));
        }
    }

    private void signalHandshakeComplete() {
        syncedLatch.countDown();
    }

    private void applyReplicationCommand(RespArray frame) throws CommandException {
        if (frame.elements().size() != 1 || !(frame.elements().getFirst() instanceof RespBulkString bulk)) {
            throw new CommandException("ERR invalid replication frame");
        }
        List<byte[]> parts = ReplicaStreamSession.decodeCommandFrame(bulk.data());
        String commandName = new String(parts.getFirst(), StandardCharsets.UTF_8);
        List<byte[]> arguments = parts.size() == 1 ? List.of() : parts.subList(1, parts.size());
        ClientReply reply = dispatcher.dispatch(replicaSession(), commandName, arguments);
        if (reply instanceof ClientReply.Error error) {
            throw new CommandException(error.message());
        }
    }

    private void applySnapshot(byte[] snapshotBytes) throws CommandException {
        try {
            DatabaseSnapshot snapshot = SnapshotReader.decode(snapshotBytes);
            KeyspaceSnapshotter.restore(coordinator.store(), coordinator.store().expirationService(), snapshot);
        } catch (Exception failure) {
            throw new CommandException("ERR failed to apply replication snapshot");
        }
    }

    private static boolean isSnapshotFrame(RespArray array) {
        return array.elements().size() == 2
                && array.elements().get(0) instanceof RespBulkString marker
                && "SNAPSHOT".equals(new String(marker.data(), StandardCharsets.UTF_8));
    }

    private static byte[] extractSnapshot(RespArray snapshotFrame) throws CommandException {
        if (snapshotFrame.elements().size() != 2
                || !(snapshotFrame.elements().get(1) instanceof RespBulkString snapshotBulk)) {
            throw new CommandException("ERR invalid replication snapshot payload");
        }
        return snapshotBulk.data();
    }

    private static ClientSession replicaSession() {
        return new ClientSession() {
            @Override
            public String id() {
                return "replica-upstream";
            }

            @Override
            public SocketChannel channel() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static void writeRespCommand(Socket socket, List<String> commandParts) throws IOException {
        List<byte[]> encoded =
                commandParts.stream().map(part -> part.getBytes(StandardCharsets.UTF_8)).toList();
        byte[] frame = AofRespCommandCodec.encode(encoded);
        socket.getOutputStream().write(frame);
        socket.getOutputStream().flush();
    }

    private static String readLine(InputStream input) throws IOException {
        StringBuilder builder = new StringBuilder();
        int previous = -1;
        while (true) {
            int value = input.read();
            if (value < 0) {
                return builder.isEmpty() ? null : builder.toString();
            }
            char ch = (char) value;
            if (previous == '\r' && ch == '\n') {
                builder.deleteCharAt(builder.length() - 1);
                return builder.toString();
            }
            builder.append(ch);
            previous = value;
        }
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
