package com.myredis.protocol;

/**
 * RESP2 bulk-string reply with binary-safe payload.
 */
public record RespBulkString(byte[] data) implements RespBulkReply {

    public RespBulkString {
        if (data == null) {
            throw new IllegalArgumentException("data");
        }
    }
}
