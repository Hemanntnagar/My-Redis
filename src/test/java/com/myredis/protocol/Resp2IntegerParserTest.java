package com.myredis.protocol;

import com.myredis.exception.ProtocolException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Resp2IntegerParserTest {

    @Test
    void decodesInteger() throws ProtocolException {
        ByteBuffer buffer = ByteBuffer.wrap(":1000\r\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(new RespInteger(1000L), Resp2IntegerParser.decodeInteger(buffer));
    }

    @Test
    void decodesNegativeInteger() throws ProtocolException {
        ByteBuffer buffer = ByteBuffer.wrap(":-42\r\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(new RespInteger(-42L), Resp2IntegerParser.decodeInteger(buffer));
    }

    @Test
    void waitsForCompleteCrlf() throws ProtocolException {
        ByteBuffer buffer = ByteBuffer.wrap(":1\r".getBytes(StandardCharsets.UTF_8));
        assertNull(Resp2IntegerParser.decodeInteger(buffer));
        assertEquals(0, buffer.position());
    }

    @Test
    void rejectsInvalidInteger() {
        ByteBuffer buffer = ByteBuffer.wrap(":abc\r\n".getBytes(StandardCharsets.UTF_8));
        ProtocolException failure =
                assertThrows(ProtocolException.class, () -> Resp2IntegerParser.decodeInteger(buffer));
        assertEquals("invalid integer in RESP payload", failure.clientMessage());
    }

    @Test
    void rejectsWrongPrefix() {
        ByteBuffer buffer = ByteBuffer.wrap("+1\r\n".getBytes(StandardCharsets.UTF_8));
        ProtocolException failure =
                assertThrows(ProtocolException.class, () -> Resp2IntegerParser.decodeInteger(buffer));
        assertEquals("expected integer prefix", failure.clientMessage());
    }
}
