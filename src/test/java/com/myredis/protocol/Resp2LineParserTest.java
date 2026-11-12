package com.myredis.protocol;

import com.myredis.exception.ProtocolException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Resp2LineParserTest {

    @Test
    void decodesSimpleString() throws ProtocolException {
        ByteBuffer buffer = ByteBuffer.wrap("+OK\r\n".getBytes(StandardCharsets.UTF_8));
        RespSimpleString decoded = Resp2LineParser.decodeSimpleString(buffer);
        assertEquals(new RespSimpleString("OK"), decoded);
        assertEquals(0, buffer.remaining());
    }

    @Test
    void decodesEmptySimpleString() throws ProtocolException {
        ByteBuffer buffer = ByteBuffer.wrap("+\r\n".getBytes(StandardCharsets.UTF_8));
        RespSimpleString decoded = Resp2LineParser.decodeSimpleString(buffer);
        assertEquals(new RespSimpleString(""), decoded);
    }

    @Test
    void simpleStringWaitsForCrlf() throws ProtocolException {
        ByteBuffer buffer = ByteBuffer.wrap("+OK\r".getBytes(StandardCharsets.UTF_8));
        assertNull(Resp2LineParser.decodeSimpleString(buffer));
        assertEquals(0, buffer.position());
    }

    @Test
    void rejectsMalformedSimpleStringCrlf() {
        ByteBuffer buffer = ByteBuffer.wrap("+OK\rX".getBytes(StandardCharsets.UTF_8));
        ProtocolException failure =
                assertThrows(ProtocolException.class, () -> Resp2LineParser.decodeSimpleString(buffer));
        assertEquals("expected LF after CR", failure.clientMessage());
    }

    @Test
    void rejectsWrongSimpleStringPrefix() {
        ByteBuffer buffer = ByteBuffer.wrap("-OK\r\n".getBytes(StandardCharsets.UTF_8));
        ProtocolException failure =
                assertThrows(ProtocolException.class, () -> Resp2LineParser.decodeSimpleString(buffer));
        assertEquals("expected simple-string prefix", failure.clientMessage());
    }

    @Test
    void decodesError() throws ProtocolException {
        ByteBuffer buffer = ByteBuffer.wrap("-ERR unknown\r\n".getBytes(StandardCharsets.UTF_8));
        RespError decoded = Resp2LineParser.decodeError(buffer);
        assertEquals(new RespError("ERR unknown"), decoded);
    }

    @Test
    void errorIncompleteFrameIsNull() throws ProtocolException {
        ByteBuffer buffer = ByteBuffer.wrap("-ERR".getBytes(StandardCharsets.UTF_8));
        assertNull(Resp2LineParser.decodeError(buffer));
        assertEquals(0, buffer.position());
    }
}
