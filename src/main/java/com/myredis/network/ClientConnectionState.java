package com.myredis.network;

import java.nio.ByteBuffer;

/**
 * Mutable per-connection buffers for incremental RESP reads and reply writes.
 */
final class ClientConnectionState {

    private static final int INITIAL_READ_CAPACITY = 8 * 1024;
    private static final int WRITE_CAPACITY = 4 * 1024;

    private ByteBuffer readBuffer = ByteBuffer.allocate(INITIAL_READ_CAPACITY);
    private final ByteBuffer writeBuffer = ByteBuffer.allocate(WRITE_CAPACITY);

    ByteBuffer readBuffer() {
        return readBuffer;
    }

    ByteBuffer writeBuffer() {
        return writeBuffer;
    }

    void prepareForRead() {
        if (!readBuffer.hasRemaining() && readBuffer.position() > 0) {
            readBuffer.compact();
        }
    }

    void flipReadBuffer() {
        readBuffer.flip();
    }

    void compactReadBuffer() {
        readBuffer.compact();
    }

    void ensureReadCapacity(int additional) {
        if (readBuffer.remaining() >= additional) {
            return;
        }
        int required = readBuffer.position() + additional;
        if (readBuffer.capacity() >= required) {
            readBuffer.compact();
            return;
        }
        int nextCapacity = Math.max(required, readBuffer.capacity() * 2);
        ByteBuffer grown = ByteBuffer.allocate(nextCapacity);
        readBuffer.flip();
        grown.put(readBuffer);
        readBuffer = grown;
    }
}
