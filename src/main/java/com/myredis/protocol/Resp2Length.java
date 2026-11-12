package com.myredis.protocol;

import com.myredis.exception.ProtocolException;

/**
 * Validates length fields in RESP2 frames.
 */
public final class Resp2Length {

    /** Upper bound on array element count to avoid unbounded allocations from hostile input. */
    public static final int MAX_ARRAY_LENGTH = 1_048_576;

    /** Upper bound on bulk-string payload bytes (512 MiB, aligned with common Redis client defaults). */
    public static final int MAX_BULK_LENGTH = 512 * 1024 * 1024;

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
        if (length > MAX_BULK_LENGTH) {
            throw new ProtocolException("bulk string length exceeds limit");
        }
        return length;
    }

    public static int parseArrayLength(String line) throws ProtocolException {
        if (line == null || line.isBlank()) {
            throw new ProtocolException("invalid array length");
        }
        int length;
        try {
            length = Integer.parseInt(line.trim());
        } catch (NumberFormatException failure) {
            throw new ProtocolException("invalid array length");
        }
        if (length < -1) {
            throw new ProtocolException("invalid array length");
        }
        if (length > MAX_ARRAY_LENGTH) {
            throw new ProtocolException("array length exceeds limit");
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
