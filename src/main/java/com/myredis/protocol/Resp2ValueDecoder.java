package com.myredis.protocol;

import com.myredis.exception.ProtocolException;

import java.nio.ByteBuffer;

/**
 * Shared incremental decoder for a single top-level RESP2 value.
 */
final class Resp2ValueDecoder {

    private Resp2ValueDecoder() {
    }

    static Object decodeNext(ByteBuffer input) throws ProtocolException {
        if (!input.hasRemaining()) {
            return null;
        }
        input.mark();
        byte type = input.get();
        return switch (type) {
            case '+' -> decodeSimpleString(input, true);
            case '-' -> decodeError(input, true);
            case ':' -> decodeInteger(input, true);
            case '$' -> decodeBulkString(input, true);
            case '*' -> decodeArray(input);
            default -> throw new ProtocolException("unknown RESP type byte");
        };
    }

    static Object incomplete(ByteBuffer input) {
        input.reset();
        return null;
    }

    private static Object decodeSimpleString(ByteBuffer input, boolean typeConsumed) throws ProtocolException {
        if (typeConsumed) {
            input.position(input.position() - 1);
        }
        RespSimpleString decoded = Resp2LineParser.decodeSimpleString(input);
        if (decoded == null) {
            return incomplete(input);
        }
        return decoded;
    }

    private static Object decodeError(ByteBuffer input, boolean typeConsumed) throws ProtocolException {
        if (typeConsumed) {
            input.position(input.position() - 1);
        }
        RespError decoded = Resp2LineParser.decodeError(input);
        if (decoded == null) {
            return incomplete(input);
        }
        return decoded;
    }

    private static Object decodeInteger(ByteBuffer input, boolean typeConsumed) throws ProtocolException {
        if (typeConsumed) {
            input.position(input.position() - 1);
        }
        RespInteger decoded = Resp2IntegerParser.decodeInteger(input);
        if (decoded == null) {
            return incomplete(input);
        }
        return decoded;
    }

    private static Object decodeArray(ByteBuffer input) throws ProtocolException {
        Object decoded = Resp2ArrayParser.decodeArrayElements(input);
        if (decoded == null) {
            return incomplete(input);
        }
        return decoded;
    }

    private static Object decodeBulkString(ByteBuffer input, boolean typeConsumed) throws ProtocolException {
        if (typeConsumed) {
            input.position(input.position() - 1);
        }
        RespBulkReply decoded = Resp2BulkStringParser.decodeBulkString(input);
        if (decoded == null) {
            return incomplete(input);
        }
        return decoded;
    }
}
