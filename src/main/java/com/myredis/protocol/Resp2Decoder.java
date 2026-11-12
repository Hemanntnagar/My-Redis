package com.myredis.protocol;

import com.myredis.exception.ProtocolException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Incremental RESP2 decoder for client command arrays and primitive reply types.
 */
public final class Resp2Decoder implements RespDecoder {

    @Override
    public Object decodeNext(ByteBuffer input) throws ProtocolException {
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

    private static Object decodeArray(ByteBuffer input) throws ProtocolException {
        String line = Resp2Crlf.readLine(input);
        if (line == null) {
            return incomplete(input);
        }
        int count;
        try {
            count = Integer.parseInt(line.trim());
        } catch (NumberFormatException failure) {
            throw new ProtocolException("invalid array length");
        }
        if (count < -1) {
            throw new ProtocolException("invalid array length");
        }
        if (count == -1) {
            return null;
        }
        List<byte[]> elements = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            if (!input.hasRemaining()) {
                return incomplete(input);
            }
            input.mark();
            RespBulkReply element = Resp2BulkStringParser.decodeBulkString(input);
            if (element == null) {
                return incomplete(input);
            }
            elements.add(
                    switch (element) {
                        case RespBulkString bulk -> bulk.data();
                        case RespNullBulk ignored -> null;
                    });
        }
        return List.copyOf(elements);
    }

    private static Object incomplete(ByteBuffer input) {
        input.reset();
        return null;
    }
}
