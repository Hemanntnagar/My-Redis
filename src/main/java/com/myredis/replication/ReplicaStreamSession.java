package com.myredis.replication;

import com.myredis.network.ConnectedClientSession;
import com.myredis.persistence.aof.AofRespCommandCodec;
import com.myredis.protocol.Resp2ArrayEncoder;
import com.myredis.protocol.RespArray;
import com.myredis.protocol.RespBulkString;
import com.myredis.protocol.RespValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks live replica sockets and pushes encoded writes from the primary.
 */
public final class ReplicaStreamSession {

    private final CopyOnWriteArrayList<ConnectedClientSession> replicas = new CopyOnWriteArrayList<>();

    public void register(ConnectedClientSession session) {
        replicas.addIfAbsent(Objects.requireNonNull(session, "session"));
    }

    public void unregister(ConnectedClientSession session) {
        replicas.remove(session);
    }

    public int connectedReplicaCount() {
        return replicas.size();
    }

    public void broadcast(byte[] encodedCommand) {
        byte[] frame = wrapReplicationFrame(encodedCommand);
        for (ConnectedClientSession replica : replicas) {
            writeFrame(replica, frame);
        }
    }

    public void sendRaw(ConnectedClientSession replica, byte[] frame) {
        writeFrame(replica, frame);
    }

    private void writeFrame(ConnectedClientSession replica, byte[] frame) {
        SocketChannel channel = replica.channel();
        if (!channel.isOpen()) {
            return;
        }
        synchronized (replica.writeLock()) {
            try {
                ByteBuffer buffer = ByteBuffer.wrap(frame);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                replica.touch();
            } catch (IOException ignored) {
                unregister(replica);
            }
        }
    }

    static byte[] wrapReplicationFrame(byte[] encodedCommand) {
        List<RespValue> elements = List.of(new RespBulkString(encodedCommand));
        try {
            ByteBuffer buffer = ByteBuffer.allocate(encodedCommand.length + 64);
            Resp2ArrayEncoder.encode(new RespArray(elements), buffer);
            buffer.flip();
            byte[] frame = new byte[buffer.remaining()];
            buffer.get(frame);
            return frame;
        } catch (Exception failure) {
            throw new IllegalStateException("failed to frame replication command", failure);
        }
    }

    static byte[] encodeCommandParts(List<byte[]> commandParts) {
        return AofRespCommandCodec.encode(commandParts);
    }

    static List<byte[]> decodeCommandFrame(byte[] encodedCommand) {
        try {
            return AofRespCommandCodec.decode(encodedCommand);
        } catch (com.myredis.exception.ProtocolException failure) {
            throw new IllegalStateException("failed to decode replication command", failure);
        }
    }

    static byte[] fullSyncMarker(long masterOffset) {
        return ("+FULLSYNC " + masterOffset + "\r\n").getBytes();
    }

    static byte[] partialSyncMarker(long masterOffset, int replayed) {
        return ("+PARTIALSYNC " + masterOffset + " " + replayed + "\r\n").getBytes();
    }

    static byte[] bulkSnapshotMarker(byte[] snapshot) {
        List<RespValue> elements = new ArrayList<>();
        elements.add(new RespBulkString("SNAPSHOT".getBytes()));
        elements.add(new RespBulkString(snapshot));
        try {
            ByteBuffer buffer = ByteBuffer.allocate(snapshot.length + 128);
            Resp2ArrayEncoder.encode(new RespArray(elements), buffer);
            buffer.flip();
            byte[] frame = new byte[buffer.remaining()];
            buffer.get(frame);
            return frame;
        } catch (Exception failure) {
            throw new IllegalStateException("failed to encode replication snapshot", failure);
        }
    }
}
