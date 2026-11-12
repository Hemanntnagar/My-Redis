package com.myredis.protocol;

import com.myredis.exception.ProtocolException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Resp2ArrayParserTest {

    @Test
    void decodesNullArray() throws ProtocolException {
        ByteBuffer buffer = ByteBuffer.wrap("*-1\r\n".getBytes(StandardCharsets.UTF_8));
        buffer.get(); // consume '*'
        assertEquals(RespNullArray.INSTANCE, Resp2ArrayParser.decodeArrayElements(buffer));
    }

    @Test
    void decodesNestedAndMixedElements() throws ProtocolException {
        String frame = "*3\r\n:42\r\n+OK\r\n*2\r\n$3\r\nfoo\r\n$-1\r\n";
        ByteBuffer buffer = ByteBuffer.wrap(frame.getBytes(StandardCharsets.UTF_8));
        buffer.get();
        Object decoded = Resp2ArrayParser.decodeArrayElements(buffer);
        RespArray array = assertInstanceOf(RespArray.class, decoded);
        assertEquals(3, array.elements().size());
        assertEquals(42L, ((RespInteger) array.elements().get(0)).value());
        assertEquals("OK", ((RespSimpleString) array.elements().get(1)).value());

        RespArray nested = assertInstanceOf(RespArray.class, array.elements().get(2));
        assertEquals(2, nested.elements().size());
        assertEquals("foo", new String(((RespBulkString) nested.elements().get(0)).data(), StandardCharsets.UTF_8));
        assertEquals(RespNullBulk.INSTANCE, nested.elements().get(1));
    }

    @Test
    void returnsNullWhenHeaderIsIncomplete() throws ProtocolException {
        ByteBuffer buffer = ByteBuffer.wrap("*12".getBytes(StandardCharsets.UTF_8));
        assertNull(new Resp2Decoder().decodeNext(buffer));
        assertEquals(0, buffer.position());
    }

    @Test
    void rejectsInvalidArrayLength() {
        ByteBuffer buffer = ByteBuffer.wrap("*-2\r\n".getBytes(StandardCharsets.UTF_8));
        buffer.get();
        ProtocolException failure =
                assertThrows(ProtocolException.class, () -> Resp2ArrayParser.decodeArrayElements(buffer));
        assertEquals("invalid array length", failure.clientMessage());
    }

    @Test
    void rejectsLengthAboveLimit() {
        int overLimit = Resp2Length.MAX_ARRAY_LENGTH + 1;
        ByteBuffer buffer = ByteBuffer.wrap(("*" + overLimit + "\r\n").getBytes(StandardCharsets.UTF_8));
        buffer.get();
        ProtocolException failure =
                assertThrows(ProtocolException.class, () -> Resp2ArrayParser.decodeArrayElements(buffer));
        assertEquals("array length exceeds limit", failure.clientMessage());
    }
}
