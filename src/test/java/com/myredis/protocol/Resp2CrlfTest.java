package com.myredis.protocol;

import com.myredis.exception.ProtocolException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Resp2CrlfTest {

    @Test
    void readLineReturnsNullUntilTerminatorArrives() throws ProtocolException {
        ByteBuffer partial = ByteBuffer.wrap("payload\r".getBytes(StandardCharsets.UTF_8));
        assertNull(Resp2Crlf.readLine(partial));

        ByteBuffer complete = ByteBuffer.wrap("payload\r\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("payload", Resp2Crlf.readLine(complete));
    }

    @Test
    void requireTerminatorRejectsLfOnly() {
        ByteBuffer buffer = ByteBuffer.wrap("\n".getBytes(StandardCharsets.UTF_8));
        ProtocolException failure = assertThrows(ProtocolException.class, () -> Resp2Crlf.requireTerminator(buffer));
        assertEquals("expected CR in CRLF", failure.clientMessage());
    }

    @Test
    void writeLineProducesCrlf() throws ProtocolException {
        ByteBuffer output = ByteBuffer.allocate(16);
        Resp2Crlf.writeLine(':', "42", output);
        output.flip();
        byte[] frame = new byte[output.remaining()];
        output.get(frame);
        assertEquals(":42\r\n", new String(frame, StandardCharsets.UTF_8));
    }
}
