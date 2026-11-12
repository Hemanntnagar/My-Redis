package com.myredis.protocol;

/**
 * RESP2 null array ({@code *-1}).
 */
public record RespNullArray() implements RespValue {

    public static final RespNullArray INSTANCE = new RespNullArray();
}
