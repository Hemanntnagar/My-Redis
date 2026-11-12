package com.myredis.exception;

/**
 * Malformed or unsupported RESP input.
 */
public final class ProtocolException extends MyRedisException {

    public ProtocolException(String clientMessage) {
        super(ErrorCategory.PROTOCOL, clientMessage);
    }

    public ProtocolException(String clientMessage, String internalDetail, Throwable cause) {
        super(ErrorCategory.PROTOCOL, clientMessage, internalDetail, cause);
    }
}
