package com.myredis.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/** Thread-safe RESP writes to a connected client socket. */
public final class ClientSocketWriter {

    private final ConnectedClientSession session;
    private final ClientReplyEncoder encoder = new ClientReplyEncoder();

    public ClientSocketWriter(ConnectedClientSession session) {
        this.session = session;
    }

    public boolean write(ClientReply reply) {
        int required = encoder.requiredBytes(reply);
        ByteBuffer buffer = ByteBuffer.allocate(Math.max(required, 256));
        encoder.encode(reply, buffer);
        buffer.flip();
        try {
            synchronized (session.writeLock()) {
                SocketChannel channel = session.channel();
                while (buffer.hasRemaining()) {
                    int written = channel.write(buffer);
                    if (written < 0) {
                        return false;
                    }
                }
            }
            session.touch();
            return true;
        } catch (IOException failure) {
            return false;
        }
    }
}
