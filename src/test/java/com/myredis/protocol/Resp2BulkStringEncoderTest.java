package com.myredis.protocol;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class Resp2BulkStringEncoderTest {

    @Test
    void encodesBinaryBulkRoundTrip() throws com.myredis.exception.ProtocolException {
        byte[] payload = new byte[] {0x01, 0x02, 0x00, '\r'};
        ByteBuffer encoded = ByteBuffer.allocate(32);
        Resp2BulkStringEncoder.encode(payload, encoded);
        encoded.flip();
        RespBulkReply decoded = Resp2BulkStringParser.decodeBulkString(encoded);
        assertArrayEquals(payload, ((RespBulkString) decoded).data());
    }

    @Test
    void encodesNullBulkRoundTrip() throws com.myredis.exception.ProtocolException {
        ByteBuffer encoded = ByteBuffer.allocate(16);
        Resp2BulkStringEncoder.encodeNullBulk(encoded);
        encoded.flip();
        byte[] frame = new byte[encoded.remaining()];
        encoded.get(frame);
        assertArrayEquals("$-1\r\n".getBytes(), frame);
    }
}
