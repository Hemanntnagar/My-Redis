package com.myredis.protocol;

import com.myredis.exception.ProtocolException;

/**
 * Validates length fields in RESP2 frames.
 */
public final class Resp2Length {

    private Resp2Length() {
    }

    public static int parseBulkLength(String line) throws ProtocolException {
        if (line == null || line.isBlank()) {
            throw new ProtocolException("invalid bulk string length");
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
        return length;
    }

    public static long parseInteger(String line) throws ProtocolException {
        if (line == null || line.isBlank()) {
            throw new ProtocolException("invalid integer in RESP payload");
        }
        try {
            return Long.parseLong(line.trim());
        } catch (NumberFormatException failure) {
            throw new ProtocolException("invalid integer in RESP payload");
        }
    }
}
