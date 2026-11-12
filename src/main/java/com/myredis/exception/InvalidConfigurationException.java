package com.myredis.exception;

/**
 * Raised when server settings fail validation during load or build.
 */
public class InvalidConfigurationException extends MyRedisException {

    public InvalidConfigurationException(String message) {
        super(message);
    }
}
