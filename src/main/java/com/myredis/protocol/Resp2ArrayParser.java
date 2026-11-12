package com.myredis.protocol;

import com.myredis.exception.ProtocolException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses RESP2 array prefixes and nested element sequences.
 */
public final class Resp2ArrayParser {

    private Resp2ArrayParser() {
    }

    /**
     * Decodes array elements after the leading {@code *} type byte has been consumed.
     *
     * @return a {@link RespArray}, {@link RespNullArray#INSTANCE}, or {@code null} if more bytes are required
     */
    public static Object decodeArrayElements(ByteBuffer input) throws ProtocolException {
        String line = Resp2Crlf.readLine(input);
        if (line == null) {
            return null;
        }
        int count = Resp2Length.parseArrayLength(line);
        if (count == -1) {
            return RespNullArray.INSTANCE;
        }
        List<RespValue> elements = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            if (!input.hasRemaining()) {
                return Resp2ValueDecoder.incomplete(input);
            }
            input.mark();
            Object element = Resp2ValueDecoder.decodeNext(input);
            if (element == null) {
                return Resp2ValueDecoder.incomplete(input);
            }
            if (!(element instanceof RespValue value)) {
                throw new ProtocolException("invalid array element");
            }
            elements.add(value);
        }
        return new RespArray(elements);
    }
}
