package com.myredis.protocol;

import com.myredis.exception.ProtocolException;

import java.nio.ByteBuffer;

/**
 * Encodes RESP2 arrays and heterogeneous child values.
 */
public final class Resp2ArrayEncoder {

    private Resp2ArrayEncoder() {
    }

    public static void encodeNullArray(ByteBuffer output) {
        output.put((byte) '*');
        output.put("-1".getBytes());
        Resp2Crlf.writeTerminator(output);
    }

    public static void encode(RespArray array, ByteBuffer output) throws ProtocolException {
        int count = array.elements().size();
        output.put((byte) '*');
        output.put(Long.toString(count).getBytes());
        Resp2Crlf.writeTerminator(output);
        for (RespValue element : array.elements()) {
            encodeValue(element, output);
        }
    }

    public static void encodeValue(RespValue value, ByteBuffer output) throws ProtocolException {
        switch (value) {
            case RespSimpleString simple -> Resp2LineEncoder.encodeSimpleString(simple, output);
            case RespError error -> Resp2LineEncoder.encodeError(error, output);
            case RespInteger integer -> Resp2IntegerEncoder.encode(integer.value(), output);
            case RespBulkString bulk -> Resp2BulkStringEncoder.encode(bulk.data(), output);
            case RespNullBulk ignored -> Resp2BulkStringEncoder.encodeNullBulk(output);
            case RespArray nested -> encode(nested, output);
            case RespNullArray ignored -> encodeNullArray(output);
            default -> throw new ProtocolException("unsupported RESP value");
        }
    }
}
