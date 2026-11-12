package com.myredis.exception;

import com.myredis.logging.LogContext;
import com.myredis.logging.StructuredServerLogger;

/**
 * Logs server failures with structure and returns client-safe error text.
 */
public final class ServerErrorHandler {

    private final StructuredServerLogger logger;

    public ServerErrorHandler(StructuredServerLogger logger) {
        this.logger = logger;
    }

    /**
     * @return message safe to embed in a RESP error reply
     */
    public String handleFailure(Throwable failure, LogContext context) {
        logger.logFailure(failure, context);
        return ErrorResponseMapper.toClientErrorMessage(failure);
    }
}
