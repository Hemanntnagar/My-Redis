package com.myredis.protocol;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Resp2EncoderLineTest {

    private final Resp2Encoder encoder = new Resp2Encoder();

    @Test
    void encoderDelegatesToLineEncoding() {
        ByteBuffer output = ByteBuffer.allocate(32);
        encoder.encodeSimpleString("OK", output);
        output.flip();
        byte[] bytes = new byte[output.remaining()];
        output.get(bytes);
        assertArrayEquals("+OK\r\n".getBytes(StandardCharsets.UTF_8), bytes);
    }

    @Test
    void encoderRejectsInvalidSimpleStringContent() {
        assertThrows(IllegalArgumentException.class, () -> encoder.encodeSimpleString("a\rb", ByteBuffer.allocate(16)));
    }
}
