package com.myredis.protocol;

import com.myredis.exception.ProtocolException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Resp2DecoderTest {

    private final Resp2Decoder decoder = new Resp2Decoder();

    @Test
    void decodesCommandArrayIncrementally() throws ProtocolException {
        byte[] frame = "*2\r\n$4\r\nPING\r\n$4\r\nPONG\r\n".getBytes(StandardCharsets.UTF_8);

        ByteBuffer firstChunk = ByteBuffer.wrap(frame, 0, 10);
        assertNull(decoder.decodeNext(firstChunk));

        ByteBuffer full = ByteBuffer.wrap(frame);
        Object decoded = decoder.decodeNext(full);
        assertTrue(decoded instanceof List<?>);
        @SuppressWarnings("unchecked")
        List<byte[]> parts = (List<byte[]>) decoded;
        assertEquals(2, parts.size());
        assertEquals("PING", new String(parts.get(0), StandardCharsets.UTF_8));
        assertEquals("PONG", new String(parts.get(1), StandardCharsets.UTF_8));
        assertNull(decoder.decodeNext(full));
    }
}
