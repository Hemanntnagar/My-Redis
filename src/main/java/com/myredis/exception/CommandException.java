package com.myredis.exception;

/**
 * Unknown command, arity mismatch, or invalid arguments for an otherwise recognized command.
 */
public final class CommandException extends MyRedisException {

    public CommandException(String clientMessage) {
        super(ErrorCategory.COMMAND, clientMessage);
    }

    public CommandException(String clientMessage, String internalDetail, Throwable cause) {
        super(ErrorCategory.COMMAND, clientMessage, internalDetail, cause);
    }
}
