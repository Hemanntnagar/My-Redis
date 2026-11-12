package com.myredis.exception;

/**
 * Base checked exception for recoverable server-side failures.
 */
public class MyRedisException extends Exception {

    public MyRedisException(String message) {
        super(message);
    }

    public MyRedisException(String message, Throwable cause) {
        super(message, cause);
    }
}
