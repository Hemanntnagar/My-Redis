package com.myredis.protocol;

import java.nio.ByteBuffer;

/**
 * Writes RESP2 replies into a client output buffer.
 */
public interface RespEncoder {

    void encodeSimpleString(String value, ByteBuffer output);

    void encodeError(String message, ByteBuffer output);

    void encodeInteger(long value, ByteBuffer output);

    void encodeBulkString(byte[] value, ByteBuffer output);

    void encodeNullBulk(ByteBuffer output);

    void encodeArray(RespArray value, ByteBuffer output);

    void encodeNullArray(ByteBuffer output);

    void encodeValue(RespValue value, ByteBuffer output);
}
