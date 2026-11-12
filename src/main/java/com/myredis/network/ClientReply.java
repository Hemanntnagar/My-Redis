package com.myredis.network;

/**
 * Logical RESP2 reply produced by {@link ClientRequestProcessor}; the connection handler encodes and writes it.
 */
public sealed interface ClientReply {

    record SimpleString(String value) implements ClientReply {}

    record Error(String message) implements ClientReply {}

    record BulkString(byte[] value) implements ClientReply {}

    record NullBulk() implements ClientReply {}

    record Integer(long value) implements ClientReply {}

    static ClientReply pong() {
        return new SimpleString("PONG");
    }

    static ClientReply error(String message) {
        return new Error(message);
    }

    static ClientReply ok() {
        return new SimpleString("OK");
    }
}
