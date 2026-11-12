package com.myredis.exception;

import java.util.Objects;

/**
 * Base checked exception for recoverable server-side failures.
 * <p>
 * {@link #clientMessage()} is safe to send to Redis clients; {@link #getMessage()} may carry
 * additional operator detail and must not be forwarded blindly to clients.
 */
public class MyRedisException extends Exception {

    private final ErrorCategory category;
    private final String clientMessage;

    public MyRedisException(ErrorCategory category, String clientMessage) {
        super(clientMessage);
        this.category = Objects.requireNonNull(category, "category");
        this.clientMessage = sanitizeClientMessage(clientMessage);
    }

    public MyRedisException(ErrorCategory category, String clientMessage, String internalDetail, Throwable cause) {
        super(internalDetail != null ? internalDetail : clientMessage, cause);
        this.category = Objects.requireNonNull(category, "category");
        this.clientMessage = sanitizeClientMessage(clientMessage);
    }

    public ErrorCategory category() {
        return category;
    }

    /**
     * Message intended for RESP error replies; never includes stack traces or causes.
     */
    public String clientMessage() {
        return clientMessage;
    }

    private static String sanitizeClientMessage(String message) {
        Objects.requireNonNull(message, "clientMessage");
        return ErrorResponseMapper.sanitizeForClient(message);
    }
}
