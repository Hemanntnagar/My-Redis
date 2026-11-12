package com.myredis.replication;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;

import static org.junit.jupiter.api.Assertions.assertNull;

class ReplicationStreamReaderTest {

    @Test
    void returnsNullOnSocketClosedDuringRead() throws Exception {
        ReplicationStreamReader reader = new ReplicationStreamReader();
        InputStream input =
                new InputStream() {
                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        throw new SocketException("Socket closed");
                    }

                    @Override
                    public int read() {
                        return -1;
                    }
                };

        assertNull(reader.readNext(input));
    }

    @Test
    void returnsNullOnConnectionResetDuringRead() throws Exception {
        ReplicationStreamReader reader = new ReplicationStreamReader();
        InputStream input =
                new InputStream() {
                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        throw new SocketException("Connection reset");
                    }

                    @Override
                    public int read() {
                        return -1;
                    }
                };

        assertNull(reader.readNext(input));
    }

    @Test
    void returnsNullOnCleanEof() throws Exception {
        ReplicationStreamReader reader = new ReplicationStreamReader();
        InputStream input =
                new InputStream() {
                    @Override
                    public int read(byte[] b, int off, int len) {
                        return -1;
                    }

                    @Override
                    public int read() {
                        return -1;
                    }
                };

        assertNull(reader.readNext(input));
    }
}
