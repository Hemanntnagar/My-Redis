package com.myredis.protocol;

import com.myredis.exception.ProtocolException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Resp2LineEncoderTest {

    @Test
    void encodesSimpleStringWithCrlf() throws ProtocolException {
        ByteBuffer output = ByteBuffer.allocate(32);
        Resp2LineEncoder.encodeSimpleString("PONG", output);
        output.flip();
        byte[] bytes = new byte[output.remaining()];
        output.get(bytes);
        assertArrayEquals("+PONG\r\n".getBytes(StandardCharsets.UTF_8), bytes);
    }

    @Test
    void encodesErrorWithCrlf() throws ProtocolException {
        ByteBuffer output = ByteBuffer.allocate(64);
        Resp2LineEncoder.encodeError(new RespError("ERR syntax"), output);
        output.flip();
        byte[] bytes = new byte[output.remaining()];
        output.get(bytes);
        assertArrayEquals("-ERR syntax\r\n".getBytes(StandardCharsets.UTF_8), bytes);
    }

    @Test
    void rejectsEmbeddedNewlinesInSimpleString() {
        ProtocolException failure =
                assertThrows(ProtocolException.class, () -> Resp2LineEncoder.encodeSimpleString("bad\n", ByteBuffer.allocate(16)));
        assertEquals("RESP line cannot contain CR or LF", failure.clientMessage());
    }

    @Test
    void roundTripSimpleString() throws ProtocolException {
        String original = "HELLO";
        ByteBuffer encoded = ByteBuffer.allocate(32);
        Resp2LineEncoder.encodeSimpleString(original, encoded);
        encoded.flip();
        RespSimpleString decoded = Resp2LineParser.decodeSimpleString(encoded);
        assertEquals(new RespSimpleString(original), decoded);
    }
}
