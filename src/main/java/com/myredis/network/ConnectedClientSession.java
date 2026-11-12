package com.myredis.network;

import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable per-connection session bound to a single {@link SocketChannel}.
 */
public final class ConnectedClientSession implements ClientSession {

    private static final AtomicLong ID_SEQUENCE = new AtomicLong();

    private final String id;
    private final SocketChannel channel;
    private final SocketAddress remoteAddress;
    private final Object writeLock = new Object();
    private volatile long lastActivityNanos = System.nanoTime();

    ConnectedClientSession(SocketChannel channel) {
        this.id = Long.toHexString(ID_SEQUENCE.incrementAndGet());
        this.channel = Objects.requireNonNull(channel, "channel");
        this.remoteAddress = channel.socket().getRemoteSocketAddress();
    }

    void recordActivity() {
        touch();
    }

    /** Updates idle tracking after successful socket I/O. */
    public void touch() {
        lastActivityNanos = System.nanoTime();
    }

    long idleNanos() {
        return System.nanoTime() - lastActivityNanos;
    }

    /** Serializes socket writes from the handler and pub/sub pushes on the same connection. */
    public Object writeLock() {
        return writeLock;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public SocketChannel channel() {
        return channel;
    }

    /** Remote endpoint as reported by the socket; useful for structured logs. */
    SocketAddress remoteAddress() {
        return remoteAddress;
    }
}
