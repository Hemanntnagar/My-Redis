package com.myredis.exception;

/**
 * Keyspace or in-memory data structure failures (type mismatch, capacity, corruption).
 */
public final class StorageException extends MyRedisException {

    public StorageException(String clientMessage) {
        super(ErrorCategory.STORAGE, clientMessage);
    }

    public StorageException(String clientMessage, String internalDetail, Throwable cause) {
        super(ErrorCategory.STORAGE, clientMessage, internalDetail, cause);
    }
}
