package com.myredis.exception;

/**
 * Raised when server settings fail validation during load or build.
 */
public final class InvalidConfigurationException extends MyRedisException {

    public InvalidConfigurationException(String message) {
        super(ErrorCategory.CONFIGURATION, message);
    }

    public InvalidConfigurationException(String message, Throwable cause) {
        super(ErrorCategory.CONFIGURATION, message, message, cause);
    }
}
