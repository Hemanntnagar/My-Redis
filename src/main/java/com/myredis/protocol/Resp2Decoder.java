package com.myredis.protocol;

import com.myredis.exception.ProtocolException;

import java.nio.ByteBuffer;

/**
 * Incremental RESP2 decoder for client command arrays and primitive reply types.
 */
public final class Resp2Decoder implements RespDecoder {

    @Override
    public Object decodeNext(ByteBuffer input) throws ProtocolException {
        return Resp2ValueDecoder.decodeNext(input);
    }
}
