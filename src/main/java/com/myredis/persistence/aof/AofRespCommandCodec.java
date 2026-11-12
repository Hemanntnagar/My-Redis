package com.myredis.persistence.aof;

import com.myredis.exception.ProtocolException;
import com.myredis.protocol.Resp2ArrayEncoder;
import com.myredis.protocol.Resp2Decoder;
import com.myredis.protocol.RespArray;
import com.myredis.protocol.RespBulkReply;
import com.myredis.protocol.RespBulkString;
import com.myredis.protocol.RespValue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Encodes and decodes one command as a RESP2 bulk-string array. */
public final class AofRespCommandCodec {

    private AofRespCommandCodec() {}

    public static byte[] encode(List<byte[]> commandParts) {
        if (commandParts.isEmpty()) {
            throw new IllegalArgumentException("commandParts must not be empty");
        }
        List<RespValue> elements = new ArrayList<>(commandParts.size());
        for (byte[] part : commandParts) {
            elements.add(new RespBulkString(part));
        }
        int capacity = estimateCapacity(commandParts);
        ByteBuffer buffer = ByteBuffer.allocate(capacity);
        try {
            Resp2ArrayEncoder.encode(new RespArray(elements), buffer);
        } catch (ProtocolException failure) {
            throw new IllegalStateException("failed to encode AOF command", failure);
        }
        buffer.flip();
        byte[] encoded = new byte[buffer.remaining()];
        buffer.get(encoded);
        return encoded;
    }

    public static List<byte[]> decode(byte[] payload) throws ProtocolException {
        Resp2Decoder decoder = new Resp2Decoder();
        ByteBuffer input = ByteBuffer.wrap(payload);
        Object decoded = decoder.decodeNext(input);
        if (!(decoded instanceof RespArray array)) {
            throw new ProtocolException("ERR AOF record is not a RESP array");
        }
        if (input.hasRemaining()) {
            throw new ProtocolException("ERR trailing bytes in AOF record");
        }
        List<byte[]> parts = new ArrayList<>(array.elements().size());
        for (RespValue element : array.elements()) {
            if (!(element instanceof RespBulkReply bulk)) {
                throw new ProtocolException("ERR AOF command argument is not a bulk string");
            }
            switch (bulk) {
                case RespBulkString string -> parts.add(string.data().clone());
                default -> throw new ProtocolException("ERR AOF command argument is not a bulk string");
            }
        }
        if (parts.isEmpty()) {
            throw new ProtocolException("ERR empty AOF command");
        }
        return List.copyOf(parts);
    }

    private static int estimateCapacity(List<byte[]> commandParts) {
        int total = 64;
        for (byte[] part : commandParts) {
            total += 32 + part.length;
        }
        return total;
    }
}
