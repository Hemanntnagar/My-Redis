package com.myredis.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * RESP2 encoder for common reply types.
 */
public final class Resp2Encoder implements RespEncoder {

    @Override
    public void encodeSimpleString(String value, ByteBuffer output) {
        writePrefixedLine('+', value, output);
    }

    @Override
    public void encodeError(String message, ByteBuffer output) {
        writePrefixedLine('-', message, output);
    }

    @Override
    public void encodeBulkString(byte[] value, ByteBuffer output) {
        if (value == null) {
            encodeNullBulk(output);
            return;
        }
        output.put((byte) '$');
        String length = Integer.toString(value.length);
        output.put(length.getBytes(StandardCharsets.UTF_8));
        output.put((byte) '\r');
        output.put((byte) '\n');
        output.put(value);
        output.put((byte) '\r');
        output.put((byte) '\n');
    }

    public void encodeNullBulk(ByteBuffer output) {
        output.put((byte) '$');
        output.put((byte) '-');
        output.put((byte) '1');
        output.put((byte) '\r');
        output.put((byte) '\n');
    }

    private static void writePrefixedLine(char prefix, String value, ByteBuffer output) {
        output.put((byte) prefix);
        output.put(value.getBytes(StandardCharsets.UTF_8));
        output.put((byte) '\r');
        output.put((byte) '\n');
    }
}
