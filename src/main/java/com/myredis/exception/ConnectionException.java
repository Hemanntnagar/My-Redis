package com.myredis.exception;

/**
 * Accept loop, socket I/O, or session lifecycle failures.
 */
public final class ConnectionException extends MyRedisException {

    public ConnectionException(String clientMessage) {
        super(ErrorCategory.CONNECTION, clientMessage);
    }

    public ConnectionException(String clientMessage, String internalDetail, Throwable cause) {
        super(ErrorCategory.CONNECTION, clientMessage, internalDetail, cause);
    }
}
