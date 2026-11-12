package com.myredis.protocol;

/**
 * RESP2 error reply ({@code -...}).
 */
public record RespError(String message) {

    public RespError {
        if (message == null) {
            throw new IllegalArgumentException("message");
        }
    }
}
