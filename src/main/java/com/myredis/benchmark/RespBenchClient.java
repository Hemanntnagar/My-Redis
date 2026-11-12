package com.myredis.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Minimal blocking RESP2 client for throughput and latency measurement. */
final class RespBenchClient implements AutoCloseable {

    private final Socket socket;
    private final InputStream input;

    RespBenchClient(String host, int port) throws IOException {
        socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(host, port), 5_000);
        input = socket.getInputStream();
    }

    long roundTrip(byte[] command) throws IOException {
        long start = System.nanoTime();
        socket.getOutputStream().write(command);
        socket.getOutputStream().flush();
        consumeOneReply();
        return System.nanoTime() - start;
    }

    void sendNoReply(byte[] command) throws IOException {
        socket.getOutputStream().write(command);
        socket.getOutputStream().flush();
        consumeOneReply();
    }

    private void consumeOneReply() throws IOException {
        int prefix = input.read();
        if (prefix < 0) {
            throw new IOException("connection closed");
        }
        switch (prefix) {
            case '+', '-', ':' -> readLine();
            case '$' -> readBulkStringPayload();
            case '*' -> readArrayPayload();
            default -> throw new IOException("unexpected RESP prefix: " + (char) prefix);
        }
    }

    private void readArrayPayload() throws IOException {
        int count = Integer.parseInt(readLine());
        if (count < 0) {
            return;
        }
        for (int index = 0; index < count; index++) {
            int type = input.read();
            if (type != '$') {
                throw new IOException("expected bulk string in array");
            }
            readBulkStringPayload();
        }
    }

    private void readBulkStringPayload() throws IOException {
        int length = Integer.parseInt(readLine());
        if (length < 0) {
            return;
        }
        skipFully(length + 2);
    }

    private String readLine() throws IOException {
        StringBuilder builder = new StringBuilder();
        int previous = -1;
        while (true) {
            int value = input.read();
            if (value < 0) {
                throw new IOException("unexpected end of stream");
            }
            char ch = (char) value;
            if (previous == '\r' && ch == '\n') {
                builder.deleteCharAt(builder.length() - 1);
                break;
            }
            builder.append(ch);
            previous = value;
        }
        return builder.toString();
    }

    private void skipFully(int bytes) throws IOException {
        byte[] buffer = new byte[Math.min(bytes, 8_192)];
        int remaining = bytes;
        while (remaining > 0) {
            int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read < 0) {
                throw new IOException("unexpected end of stream");
            }
            remaining -= read;
        }
    }

  static byte[] setCommand(byte[] key, byte[] value) {
        byte[][] parts = {key, value};
        return respCommand("SET".getBytes(StandardCharsets.US_ASCII), parts);
    }

    static byte[] getCommand(byte[] key) {
        return respCommand("GET".getBytes(StandardCharsets.US_ASCII), new byte[][] {key});
    }

    static byte[] incrCommand(byte[] key) {
        return respCommand("INCR".getBytes(StandardCharsets.US_ASCII), new byte[][] {key});
    }

    static byte[] pingCommand() {
        return respCommand("PING".getBytes(StandardCharsets.US_ASCII), new byte[0][]);
    }

    static byte[] respCommand(byte[] command, byte[][] arguments) {
        int arity = 1 + arguments.length;
        int total = 16 + command.length;
        for (byte[] argument : arguments) {
            total += argument.length + 16;
        }
        byte[] encoded = new byte[total];
        int offset = writeAscii(encoded, 0, "*");
        offset = writeNumber(encoded, offset, arity);
        offset = writeAscii(encoded, offset, "\r\n$");
        offset = writeNumber(encoded, offset, command.length);
        offset = writeAscii(encoded, offset, "\r\n");
        offset = writeBytes(encoded, offset, command);
        offset = writeAscii(encoded, offset, "\r\n");
        for (byte[] argument : arguments) {
            offset = writeAscii(encoded, offset, "$");
            offset = writeNumber(encoded, offset, argument.length);
            offset = writeAscii(encoded, offset, "\r\n");
            offset = writeBytes(encoded, offset, argument);
            offset = writeAscii(encoded, offset, "\r\n");
        }
        return Arrays.copyOf(encoded, offset);
    }

    private static int writeAscii(byte[] target, int offset, String text) {
        for (int index = 0; index < text.length(); index++) {
            target[offset++] = (byte) text.charAt(index);
        }
        return offset;
    }

    private static int writeBytes(byte[] target, int offset, byte[] data) {
        System.arraycopy(data, 0, target, offset, data.length);
        return offset + data.length;
    }

    private static int writeNumber(byte[] target, int offset, int value) {
        String text = Integer.toString(value);
        return writeAscii(target, offset, text);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
