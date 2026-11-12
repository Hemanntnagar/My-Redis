package com.myredis.protocol;

/**
 * RESP2 null bulk-string reply ({@code $-1}).
 */
public record RespNullBulk() implements RespBulkReply {

    public static final RespNullBulk INSTANCE = new RespNullBulk();
}
