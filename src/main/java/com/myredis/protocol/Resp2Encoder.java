package com.myredis.protocol;

import com.myredis.exception.ProtocolException;

import java.nio.ByteBuffer;

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
    public void encodeInteger(long value, ByteBuffer output) {
        Resp2IntegerEncoder.encode(value, output);
    }

    @Override
    public void encodeBulkString(byte[] value, ByteBuffer output) {
        if (value == null) {
            encodeNullBulk(output);
            return;
        }
        Resp2BulkStringEncoder.encode(value, output);
    }

    @Override
    public void encodeNullBulk(ByteBuffer output) {
        Resp2BulkStringEncoder.encodeNullBulk(output);
    }
}
