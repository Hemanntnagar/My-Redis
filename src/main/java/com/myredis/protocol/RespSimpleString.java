package com.myredis.protocol;

/**
 * RESP2 simple-string reply ({@code +...}).
 */
public record RespSimpleString(String value) {

    public RespSimpleString {
        if (value == null) {
            throw new IllegalArgumentException("value");
        }
    }
}
