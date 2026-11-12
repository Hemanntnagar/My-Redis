package com.myredis.protocol;

import com.myredis.exception.ProtocolException;

import java.nio.ByteBuffer;

/**
 * Parses RESP2 bulk-string replies, including null bulk.
 */
public final class Resp2BulkStringParser {

    private Resp2BulkStringParser() {
    }

    /**
     * Decodes a bulk-string frame starting at the {@code $} type byte.
     *
     * @return the parsed value, or {@code null} if the frame is incomplete
     */
    public static RespBulkReply decodeBulkString(ByteBuffer input) throws ProtocolException {
        input.mark();
        if (!input.hasRemaining()) {
            input.reset();
            return null;
        }
        if (input.get() != '$') {
            throw new ProtocolException("expected bulk-string prefix");
        }
        RespBulkReply decoded = decodeBulkPayload(input);
        if (decoded == null) {
            input.reset();
        }
        return decoded;
    }

    /**
     * Reads the length line and payload after the {@code $} prefix has been consumed.
     *
     * @return the parsed value, or {@code null} if more bytes are required
     */
    static RespBulkReply decodeBulkPayload(ByteBuffer input) throws ProtocolException {
        String lengthLine = Resp2Crlf.readLine(input);
        if (lengthLine == null) {
            return null;
        }
        int length = Resp2Length.parseBulkLength(lengthLine);
        if (length == -1) {
            return RespNullBulk.INSTANCE;
        }
        if (input.remaining() < length + 2) {
            return null;
        }
        byte[] payload = new byte[length];
        input.get(payload);
        Resp2Crlf.requireTerminator(input);
        return new RespBulkString(payload);
    }

}
