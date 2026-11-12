package com.myredis.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Encodes RESP2 integer replies.
 */
public final class Resp2IntegerEncoder {

    private Resp2IntegerEncoder() {
    }

    public static void encode(long value, ByteBuffer output) {
        output.put((byte) ':');
        output.put(Long.toString(value).getBytes(StandardCharsets.UTF_8));
        Resp2Crlf.writeTerminator(output);
    }

    public static void encode(RespInteger value, ByteBuffer output) {
        encode(value.value(), output);
    }
}
