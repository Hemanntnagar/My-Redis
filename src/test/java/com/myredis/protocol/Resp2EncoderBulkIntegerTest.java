package com.myredis.protocol;

import com.myredis.exception.ProtocolException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Resp2EncoderBulkIntegerTest {

    private final Resp2Encoder encoder = new Resp2Encoder();

    @Test
    void encoderRoundTripsIntegerAndNullBulk() throws ProtocolException {
        ByteBuffer integer = ByteBuffer.allocate(16);
        encoder.encodeInteger(12L, integer);
        integer.flip();
        assertEquals(new RespInteger(12L), Resp2IntegerParser.decodeInteger(integer));

        ByteBuffer nullBulk = ByteBuffer.allocate(16);
        encoder.encodeNullBulk(nullBulk);
        nullBulk.flip();
        assertEquals(RespNullBulk.INSTANCE, Resp2BulkStringParser.decodeBulkString(nullBulk));
    }
}
