package com.myredis.protocol;

import com.myredis.exception.ProtocolException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Resp2BulkStringParserTest {

    @Test
    void decodesBinaryBulkString() throws ProtocolException {
        byte[] payload = new byte[] {0x00, '\r', '\n', (byte) 0xFF};
        ByteBuffer buffer = ByteBuffer.wrap(encodeFrame(payload));
        RespBulkReply decoded = Resp2BulkStringParser.decodeBulkString(buffer);
        assertEquals(RespBulkString.class, decoded.getClass());
        assertArrayEquals(payload, ((RespBulkString) decoded).data());
    }

    @Test
    void decodesNullBulk() throws ProtocolException {
        ByteBuffer buffer = ByteBuffer.wrap("$-1\r\n".getBytes());
        assertEquals(RespNullBulk.INSTANCE, Resp2BulkStringParser.decodeBulkString(buffer));
    }

    @Test
    void waitsForLengthLine() throws ProtocolException {
        ByteBuffer buffer = ByteBuffer.wrap("$3\r".getBytes());
        assertNull(Resp2BulkStringParser.decodeBulkString(buffer));
        assertEquals(0, buffer.position());
    }

    @Test
    void waitsForPayloadAndCrlf() throws ProtocolException {
        byte[] frame = encodeFrame(new byte[] {'a', 'b', 'c'});
        ByteBuffer buffer = ByteBuffer.wrap(frame, 0, frame.length - 1);
        assertNull(Resp2BulkStringParser.decodeBulkString(buffer));
        assertEquals(0, buffer.position());
    }

    @Test
    void rejectsLengthAboveLimit() {
        int overLimit = Resp2Length.MAX_BULK_LENGTH + 1;
        ByteBuffer buffer = ByteBuffer.wrap(("$" + overLimit + "\r\n").getBytes());
        ProtocolException failure =
                assertThrows(ProtocolException.class, () -> Resp2BulkStringParser.decodeBulkString(buffer));
        assertEquals("bulk string length exceeds limit", failure.clientMessage());
    }

    @Test
    void rejectsInvalidLength() {
        ByteBuffer buffer = ByteBuffer.wrap("$-2\r\n".getBytes());
        ProtocolException failure =
                assertThrows(ProtocolException.class, () -> Resp2BulkStringParser.decodeBulkString(buffer));
        assertEquals("invalid bulk string length", failure.clientMessage());
    }

    @Test
    void rejectsMalformedTerminatorAfterPayload() {
        byte[] frame = "$2\r\nabX\n".getBytes();
        ProtocolException failure =
                assertThrows(ProtocolException.class, () -> Resp2BulkStringParser.decodeBulkString(ByteBuffer.wrap(frame)));
        assertEquals("expected CR in CRLF", failure.clientMessage());
    }

    private static byte[] encodeFrame(byte[] payload) {
        String header = "$" + payload.length + "\r\n";
        byte[] frame = new byte[header.length() + payload.length + 2];
        System.arraycopy(header.getBytes(), 0, frame, 0, header.length());
        System.arraycopy(payload, 0, frame, header.length(), payload.length);
        frame[frame.length - 2] = '\r';
        frame[frame.length - 1] = '\n';
        return frame;
    }
}
