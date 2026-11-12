package com.myredis.exception;

/**
 * Malformed or unsupported RESP input.
 */
public final class ProtocolException extends MyRedisException {

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
