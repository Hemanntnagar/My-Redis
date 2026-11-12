package com.myredis.exception;

/**
 * RDB/AOF load, flush, or filesystem errors.
 */
public final class PersistenceException extends MyRedisException {

    public PersistenceException(String clientMessage) {
        super(ErrorCategory.PERSISTENCE, clientMessage);
    }

    public PersistenceException(String clientMessage, String internalDetail, Throwable cause) {
        super(ErrorCategory.PERSISTENCE, clientMessage, internalDetail, cause);
    }

    public static PersistenceException wrap(String clientMessage, Throwable cause) {
        String detail = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        return new PersistenceException(clientMessage, detail, cause);
    }
}
