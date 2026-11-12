package com.myredis.replication;

import com.myredis.exception.ProtocolException;
import com.myredis.protocol.Resp2Decoder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/** Incrementally reads RESP values from a blocking replication socket. */
public final class ReplicationStreamReader {

    private final Resp2Decoder decoder = new Resp2Decoder();
    private ByteBuffer readBuffer = ByteBuffer.allocate(64 * 1024);

    public void seed(byte[] bytes) {
        growIfNeeded(bytes.length);
        readBuffer.put(bytes);
    }

    public Object readNext(InputStream input) throws IOException {
        while (true) {
            readBuffer.flip();
            while (readBuffer.hasRemaining()) {
                int before = readBuffer.position();
                try {
                    Object decoded = decoder.decodeNext(readBuffer);
                    if (decoded != null) {
                        readBuffer.compact();
                        return decoded;
                    }
                    readBuffer.position(before);
                    break;
                } catch (ProtocolException failure) {
                    throw new IOException(failure.getMessage(), failure);
                }
            }
            readBuffer.compact();
            if (readBuffer.remaining() == 0) {
                growIfNeeded(1024);
            }
            int read = input.read(readBuffer.array(), readBuffer.position(), readBuffer.remaining());
            if (read < 0) {
                return null;
            }
            if (read == 0) {
                continue;
            }
            readBuffer.position(readBuffer.position() + read);
        }
    }

    private void growIfNeeded(int additional) {
        if (readBuffer.remaining() >= additional) {
            return;
        }
        int required = readBuffer.position() + additional;
        if (readBuffer.capacity() >= required) {
            return;
        }
        int next = Math.max(required, readBuffer.capacity() * 2);
        ByteBuffer grown = ByteBuffer.allocate(next);
        readBuffer.flip();
        grown.put(readBuffer);
        readBuffer = grown;
    }
}
