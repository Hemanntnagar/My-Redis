package com.myredis.protocol;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class Resp2ArrayEncoderTest {

    private final Resp2Decoder decoder = new Resp2Decoder();

    @Test
    void encodesNullArray() {
        ByteBuffer encoded = ByteBuffer.allocate(8);
        Resp2ArrayEncoder.encodeNullArray(encoded);
        encoded.flip();
        byte[] frame = new byte[encoded.remaining()];
        encoded.get(frame);
        assertArrayEquals("*-1\r\n".getBytes(StandardCharsets.UTF_8), frame);
    }

    @Test
    void roundTripsNestedMixedArray() throws Exception {
        RespArray value =
                new RespArray(
                        List.of(
                                new RespInteger(7L),
                                new RespSimpleString("hi"),
                                new RespArray(
                                        List.of(new RespBulkString("x".getBytes(StandardCharsets.UTF_8))))));

        ByteBuffer encoded = ByteBuffer.allocate(128);
        Resp2ArrayEncoder.encode(value, encoded);
        encoded.flip();

        RespArray decoded = assertInstanceOf(RespArray.class, decoder.decodeNext(encoded));
        assertEquals(3, decoded.elements().size());
        assertEquals(7L, ((RespInteger) decoded.elements().get(0)).value());
        assertEquals("hi", ((RespSimpleString) decoded.elements().get(1)).value());
        RespArray nested = assertInstanceOf(RespArray.class, decoded.elements().get(2));
        assertArrayEquals("x".getBytes(StandardCharsets.UTF_8), ((RespBulkString) nested.elements().getFirst()).data());
    }
}
