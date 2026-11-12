package com.myredis.network;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConnectionStateTest {

    @Test
    void growsReadBufferWhenIncompleteFrameFillsCapacity() {
        ClientConnectionState state = new ClientConnectionState();
        ByteBuffer readBuffer = state.readBuffer();
        byte[] partial = "*1\r\n$".getBytes(StandardCharsets.UTF_8);
        int capacity = readBuffer.capacity();
        byte[] fill = new byte[capacity - partial.length];
        readBuffer.put(partial);
        readBuffer.put(fill);
        state.flipReadBuffer();
        state.compactReadBuffer(); // incomplete frame retained; read buffer full

        state.prepareForRead(); // must grow rather than overwrite retained bytes

        assertTrue(state.readBuffer().hasRemaining(), "must retain space for the next socket read");
        assertTrue(
                state.readBuffer().capacity() > capacity,
                "read buffer should grow past the initial capacity");
    }

    @Test
    void growsWriteBufferOnDemand() {
        ClientConnectionState state = new ClientConnectionState();
        int initial = state.writeBuffer().capacity();
        state.ensureWriteCapacity(initial * 4);
        assertTrue(state.writeBuffer().capacity() >= initial * 4);
    }
}
