package com.myredis.protocol;

import com.myredis.exception.ProtocolException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Incremental RESP2 decoder for client command arrays and primitive reply types.
 */
public final class Resp2Decoder implements RespDecoder {

    private static final byte[] INCOMPLETE = new byte[0];

    @Override
    public Object decodeNext(ByteBuffer input) throws ProtocolException {
        if (!input.hasRemaining()) {
            return null;
        }
        input.mark();
        byte type = input.get();
        return switch (type) {
            case '+' -> decodeSimpleString(input);
            case '-' -> decodeError(input);
            case ':' -> decodeInteger(input);
            case '$' -> decodeBulkString(input);
            case '*' -> decodeArray(input);
            default -> throw new ProtocolException("unknown RESP type byte");
        };
    }

    private static Object decodeSimpleString(ByteBuffer input) throws ProtocolException {
        String line = readLine(input);
        if (line == null) {
            return incomplete(input);
        }
        return line;
    }

    private static Object decodeError(ByteBuffer input) throws ProtocolException {
        String line = readLine(input);
        if (line == null) {
            return incomplete(input);
        }
        return line;
    }

    private static Object decodeInteger(ByteBuffer input) throws ProtocolException {
        String line = readLine(input);
        if (line == null) {
            return incomplete(input);
        }
        try {
            return Long.parseLong(line.trim());
        } catch (NumberFormatException failure) {
            throw new ProtocolException("invalid integer in RESP payload");
        }
    }

    private static Object decodeBulkString(ByteBuffer input) throws ProtocolException {
        BulkRead bulk = readBulk(input);
        if (bulk == null) {
            return incomplete(input);
        }
        if (bulk.nullBulk) {
            return null;
        }
        return bulk.bytes;
    }

    private static Object decodeArray(ByteBuffer input) throws ProtocolException {
        String line = readLine(input);
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
            byte nestedType = input.get();
            if (nestedType != '$') {
                throw new ProtocolException("expected bulk string in command array");
            }
            BulkRead bulk = readBulk(input);
            if (bulk == null) {
                return incomplete(input);
            }
            if (bulk.nullBulk) {
                elements.add(null);
            } else {
                elements.add(bulk.bytes);
            }
        }
        return List.copyOf(elements);
    }

    private static BulkRead readBulk(ByteBuffer input) throws ProtocolException {
        String line = readLine(input);
        if (line == null) {
            return null;
        }
        int length;
        try {
            length = Integer.parseInt(line.trim());
        } catch (NumberFormatException failure) {
            throw new ProtocolException("invalid bulk string length");
        }
        if (length < -1) {
            throw new ProtocolException("invalid bulk string length");
        }
        if (length == -1) {
            return BulkRead.nil();
        }
        if (input.remaining() < length + 2) {
            return null;
        }
        byte[] payload = new byte[length];
        input.get(payload);
        if (input.get() != '\r' || input.get() != '\n') {
            throw new ProtocolException("expected CRLF after bulk payload");
        }
        return BulkRead.of(payload);
    }

    private static String readLine(ByteBuffer input) throws ProtocolException {
        int start = input.position();
        int limit = input.limit();
        for (int index = start; index < limit; index++) {
            if (input.get(index) != '\r') {
                continue;
            }
            if (index + 1 >= limit) {
                return null;
            }
            if (input.get(index + 1) != '\n') {
                throw new ProtocolException("expected LF after CR");
            }
            int length = index - start;
            byte[] lineBytes = new byte[length];
            input.position(start);
            input.get(lineBytes, 0, length);
            input.position(index + 2);
            return new String(lineBytes, StandardCharsets.UTF_8);
        }
        return null;
    }

    private static Object incomplete(ByteBuffer input) {
        input.reset();
        return null;
    }

    private record BulkRead(boolean nullBulk, byte[] bytes) {

        static BulkRead nil() {
            return new BulkRead(true, INCOMPLETE);
        }

        static BulkRead of(byte[] bytes) {
            return new BulkRead(false, bytes);
        }
    }
}
