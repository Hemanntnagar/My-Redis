package com.myredis.protocol;

import com.myredis.exception.ProtocolException;

import java.nio.ByteBuffer;

/**
 * Parses RESP2 simple-string and error line replies.
 */
public final class Resp2LineParser {

    private Resp2LineParser() {
    }

    /**
     * Decodes a simple-string frame starting at the {@code +} type byte.
     *
     * @return the parsed value, or {@code null} if the frame is incomplete
     */
    public static RespSimpleString decodeSimpleString(ByteBuffer input) throws ProtocolException {
        input.mark();
        if (!input.hasRemaining()) {
            input.reset();
            return null;
        }
        if (input.get() != '+') {
            throw new ProtocolException("expected simple-string prefix");
        }
        String line = Resp2Crlf.readLine(input);
        if (line == null) {
            input.reset();
            return null;
        }
        return new RespSimpleString(line);
    }

    /**
     * Decodes an error frame starting at the {@code -} type byte.
     *
     * @return the parsed value, or {@code null} if the frame is incomplete
     */
    public static RespError decodeError(ByteBuffer input) throws ProtocolException {
        input.mark();
        if (!input.hasRemaining()) {
            input.reset();
            return null;
        }
        if (input.get() != '-') {
            throw new ProtocolException("expected error prefix");
        }
        String line = Resp2Crlf.readLine(input);
        if (line == null) {
            input.reset();
            return null;
        }
        return new RespError(line);
    }
}
