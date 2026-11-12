package com.myredis.protocol;

import com.myredis.exception.ProtocolException;

import java.nio.ByteBuffer;

/**
 * Incremental RESP2 decoder operating on a shared read buffer.
 */
public interface RespDecoder {

    /**
     * @return the next decoded RESP value, or {@code null} if more bytes are required
     */
    Object decodeNext(ByteBuffer input) throws ProtocolException;
}
