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

    /**
     * RESP2 array reply; {@code null} elements encode as null bulk strings (for {@code MGET}).
     */
    record Array(java.util.List<byte[]> elements) implements ClientReply {}

    /** RESP2 array whose elements are arbitrary nested replies ({@code EXEC}, pub/sub acknowledgements). */
    record CompositeArray(java.util.List<ClientReply> elements) implements ClientReply {}

    /** Multiple back-to-back RESP replies on one write (e.g. {@code SUBSCRIBE} with several channels). */
    record Sequence(java.util.List<ClientReply> replies) implements ClientReply {}

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
