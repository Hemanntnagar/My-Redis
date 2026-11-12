package com.myredis.protocol;

import com.myredis.exception.ProtocolException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Shared CRLF handling for RESP2 line-oriented types.
 */
public final class Resp2Crlf {

    private Resp2Crlf() {
    }

    /**
     * Reads bytes up to but not including the terminating {@code CR LF}.
     *
     * @return the line body, or {@code null} if the terminator is not yet available
     */
    public static String readLine(ByteBuffer input) throws ProtocolException {
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

    public static void writeLine(char typePrefix, String body, ByteBuffer output) throws ProtocolException {
        ensureNoEmbeddedNewlines(body);
        output.put((byte) typePrefix);
        output.put(body.getBytes(StandardCharsets.UTF_8));
        writeTerminator(output);
    }

    public static void writeTerminator(ByteBuffer output) {
        output.put((byte) '\r');
        output.put((byte) '\n');
    }

    public static void requireTerminator(ByteBuffer input) throws ProtocolException {
        if (!input.hasRemaining()) {
            throw new ProtocolException("expected CRLF");
        }
        if (input.get() != '\r') {
            throw new ProtocolException("expected CR in CRLF");
        }
        if (!input.hasRemaining()) {
            throw new ProtocolException("expected CRLF");
        }
        if (input.get() != '\n') {
            throw new ProtocolException("expected LF after CR");
        }
    }

    static void ensureNoEmbeddedNewlines(String value) throws ProtocolException {
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new ProtocolException("RESP line cannot contain CR or LF");
        }
    }
}
