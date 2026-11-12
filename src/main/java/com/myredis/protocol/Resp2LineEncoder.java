package com.myredis.protocol;

import com.myredis.exception.ProtocolException;

import java.nio.ByteBuffer;

/**
 * Encodes RESP2 simple-string and error line replies.
 */
public final class Resp2LineEncoder {

    private Resp2LineEncoder() {
    }

    public static void encodeSimpleString(RespSimpleString value, ByteBuffer output) throws ProtocolException {
        Resp2Crlf.writeLine('+', value.value(), output);
    }

    public static void encodeSimpleString(String value, ByteBuffer output) throws ProtocolException {
        Resp2Crlf.writeLine('+', value, output);
    }

    public static void encodeError(RespError value, ByteBuffer output) throws ProtocolException {
        Resp2Crlf.writeLine('-', value.message(), output);
    }

    public static void encodeError(String message, ByteBuffer output) throws ProtocolException {
        Resp2Crlf.writeLine('-', message, output);
    }
}
