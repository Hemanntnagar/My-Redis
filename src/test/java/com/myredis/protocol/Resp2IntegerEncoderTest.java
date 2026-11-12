package com.myredis.protocol;

import com.myredis.exception.ProtocolException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Resp2IntegerEncoderTest {

    @Test
    void roundTripInteger() throws ProtocolException {
        ByteBuffer encoded = ByteBuffer.allocate(32);
        Resp2IntegerEncoder.encode(-7L, encoded);
        encoded.flip();
        assertEquals(new RespInteger(-7L), Resp2IntegerParser.decodeInteger(encoded));
    }
}
