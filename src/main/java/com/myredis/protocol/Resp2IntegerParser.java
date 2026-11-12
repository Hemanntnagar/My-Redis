package com.myredis.protocol;

import com.myredis.exception.ProtocolException;

import java.nio.ByteBuffer;

/**
 * Parses RESP2 integer replies.
 */
public final class Resp2IntegerParser {

    private Resp2IntegerParser() {
    }

    /**
     * Decodes an integer frame starting at the {@code :} type byte.
     *
     * @return the parsed value, or {@code null} if the frame is incomplete
     */
    public static RespInteger decodeInteger(ByteBuffer input) throws ProtocolException {
        input.mark();
        if (!input.hasRemaining()) {
            input.reset();
            return null;
        }
        if (input.get() != ':') {
            throw new ProtocolException("expected integer prefix");
        }
        String line = Resp2Crlf.readLine(input);
        if (line == null) {
            input.reset();
            return null;
        }
        return new RespInteger(Resp2Length.parseInteger(line));
    }
}
