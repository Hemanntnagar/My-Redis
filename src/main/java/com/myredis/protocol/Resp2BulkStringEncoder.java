package com.myredis.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Encodes RESP2 bulk-string replies, including null bulk.
 */
public final class Resp2BulkStringEncoder {

    private Resp2BulkStringEncoder() {
    }

    public static void encode(byte[] value, ByteBuffer output) {
        output.put((byte) '$');
        output.put(Integer.toString(value.length).getBytes(StandardCharsets.UTF_8));
        Resp2Crlf.writeTerminator(output);
        output.put(value);
        Resp2Crlf.writeTerminator(output);
    }

    public static void encode(RespBulkString value, ByteBuffer output) {
        encode(value.data(), output);
    }

    public static void encodeNullBulk(ByteBuffer output) {
        output.put((byte) '$');
        output.put((byte) '-');
        output.put((byte) '1');
        Resp2Crlf.writeTerminator(output);
    }

    public static void encode(RespBulkReply value, ByteBuffer output) {
        switch (value) {
            case RespBulkString bulk -> encode(bulk, output);
            case RespNullBulk ignored -> encodeNullBulk(output);
        }
    }
}
