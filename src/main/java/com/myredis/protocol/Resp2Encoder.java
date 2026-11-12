package com.myredis.protocol;

import com.myredis.exception.ProtocolException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * RESP2 encoder for common reply types.
 */
public final class Resp2Encoder implements RespEncoder {

    @Override
    public void encodeSimpleString(String value, ByteBuffer output) {
        try {
            Resp2LineEncoder.encodeSimpleString(value, output);
        } catch (ProtocolException failure) {
            throw new IllegalArgumentException(failure.getMessage(), failure);
        }
    }

    @Override
    public void encodeError(String message, ByteBuffer output) {
        try {
            Resp2LineEncoder.encodeError(message, output);
        } catch (ProtocolException failure) {
            throw new IllegalArgumentException(failure.getMessage(), failure);
        }
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
        Resp2Crlf.writeTerminator(output);
    }
}
